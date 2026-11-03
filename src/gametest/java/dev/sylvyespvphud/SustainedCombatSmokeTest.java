package dev.sylvyespvphud;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import static dev.sylvyespvphud.ShieldDamageSmokeTest.*;

/** Sustained packet streams and actual client attacks over a dedicated-server connection. */
public final class SustainedCombatSmokeTest implements FabricClientGameTest {
    private enum Proof { SOUND, WEAR, BOTH }
    private record Delivery(long due,int damage) {}

    private static void streams(Minecraft mc,Target target,CombatTestClock clock) {
        for(boolean calculated:new boolean[]{true,false})for(var proof:Proof.values())for(int delay:new int[]{100,300,800}) {
            configure(mc,calculated,true);fresh(target);mc.player.fallDistance=0;
            long start=CombatTiming.now();var deliveries=new ArrayList<Delivery>();int damage=0;long firstVisible=-1;
            long settling=CombatTiming.settling(CombatTiming.ping());
            long wait=!calculated&&proof==Proof.SOUND?CombatTiming.retention(CombatTiming.ping()):settling;
            for(int elapsed=0;elapsed<=4000+delay+2500;elapsed+=50) {
                if(elapsed<4000&&elapsed%100==0) {
                    CombatTestClock.cooldown(mc,20+10*(elapsed/100%3));
                    double raw=MaceDamageCalculator.atAttack(mc.player,target);
                    int cost=(int)ShieldDamageCalculator.potential(target,mc.player.getMainHandItem().getDamageSource(mc.player)).getFirst().confirmed().durability(raw,false);
                    check(cost>0,"Stream fixture must cause positive vanilla shield wear");
                    damage+=cost;DamageHud.attacked(target);deliveries.add(new Delivery(start+elapsed+delay,damage));
                }
                var iterator=deliveries.iterator();
                while(iterator.hasNext()) {
                    var delivery=iterator.next();if(delivery.due()>CombatTiming.now())continue;
                    if(proof!=Proof.WEAR)sound(mc,target);
                    if(proof!=Proof.SOUND)equip(mc,target,delivery.damage());
                    iterator.remove();
                }
                DamageHud.tick(mc);
                if(ShieldDamageHud.hitVisible()&&firstVisible<0)firstVisible=elapsed;
                if(elapsed>=delay+wait+50&&elapsed<4000)check(ShieldDamageHud.hitVisible(),"No output during sustained fight: "+calculated+"/"+proof+"/"+delay+" at "+elapsed);
                clock.advance(50);
            }
            check(firstVisible>=0&&firstVisible<=delay+wait+50,"Confirmation missed its independent deadline: "+calculated+"/"+proof+"/"+delay+": "+firstVisible);
            near(damage,ShieldDamageHud.TRACKER.total(target.getUUID()),"Sustained mixed-cooldown shield total: "+calculated+"/"+proof+"/"+delay);
            near(0,CumulativeDamageTracker.total(target.getUUID()),"Sustained full blocks cannot add health damage");
        }
        for(boolean calculated:new boolean[]{true,false}) {
            configure(mc,calculated,true);fresh(target);mc.player.fallDistance=0;
            CombatTestClock.cooldown(mc,20);double first=MaceDamageCalculator.atAttack(mc.player,target);
            DamageHud.attacked(target);CombatTestClock.charge(mc);double second=MaceDamageCalculator.atAttack(mc.player,target);DamageHud.attacked(target);
            int cost=(int)(Math.floor(first)+1+Math.floor(second)+1);
            equip(mc,target,cost);clock.settle(mc);
            near(cost,ShieldDamageHud.TRACKER.total(target.getUUID()),"Coalesced wear with different saved estimates");
            equip(mc,target,cost);sound(mc,target);clock.settle(mc);
            near(cost,ShieldDamageHud.TRACKER.total(target.getUUID()),"Late sound and duplicate wear cannot double count a coalesced batch");
        }
        for(boolean calculated:new boolean[]{true,false}) {
            configure(mc,calculated,true);fresh(target);mc.player.fallDistance=0;
            DamageHud.attacked(target);sound(mc,target);clock.advance(1000);
            CombatTestClock.cooldown(mc,30);double raw=MaceDamageCalculator.atAttack(mc.player,target);
            DamageHud.attacked(target);clock.advance(1100);
            int cost=(int)Math.floor(raw)+1;equip(mc,target,cost);clock.settle(mc);
            near(cost,ShieldDamageHud.TRACKER.total(target.getUUID()),"Expired observation cannot absorb a newer shield measurement");
        }
        configure(mc,true,true);fresh(target);mc.player.fallDistance=0;
        target.getOffhandItem().setDamageValue(target.getOffhandItem().getMaxDamage()-2);
        DamageHud.attacked(target);
        mc.getConnection().handleEntityEvent(new net.minecraft.network.protocol.game.ClientboundEntityEventPacket(target,(byte)48));
        clock.settle(mc);near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Silent break resets stored shield total");
        target.setItemSlot(EquipmentSlot.OFFHAND,shield(1));CombatTestClock.charge(mc);
        double replacement=MaceDamageCalculator.atAttack(mc.player,target);DamageHud.attacked(target);sound(mc,target);clock.settle(mc);
        near(Math.floor(replacement)+1,ShieldDamageHud.TRACKER.total(target.getUUID()),"Broken shield cannot consume replacement shield's first sound-only confirmation");
    }
    private static void ticks(ClientGameTestContext context,CombatTestClock clock,int ticks) {
        for(int i=0;i<ticks;i++){context.runOnClient(mc->clock.advance(50));context.waitTick();}
    }
    private static int testPort() {
        try(var socket=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
    @Override public void runTest(ClientGameTestContext context) {
        var original=new AtomicReference<DamageConfig>();
        try {
            try(var world=context.worldBuilder().create()) {
                world.getConnection().waitForChunksRender();
                context.runOnClient(mc->{
                    original.set(SylvyesPvPHud.DAMAGE_CONFIG.current());DamageHud.tick(mc);
                    try(var clock=new CombatTestClock()) {
                        var target=new Target(mc.level);target.setId(2000040);target.setPos(mc.player.getX(),mc.player.getY(),mc.player.getZ()+2);mc.level.addEntity(target);
                        streams(mc,target,clock);mc.level.removeEntity(target.getId(),Entity.RemovalReason.DISCARDED);CombatTestClock.reset(mc);
                    }
                });
            }
            var properties=new Properties();properties.setProperty("online-mode","false");properties.setProperty("enforce-secure-profile","false");
            properties.setProperty("view-distance","3");properties.setProperty("simulation-distance","3");
            properties.setProperty("server-ip","127.0.0.1");properties.setProperty("server-port",Integer.toString(testPort()));
            var target=new AtomicReference<ServerPlayer>();var clock=new AtomicReference<CombatTestClock>();
            // Use vanilla's development startup in this disposable Fabric development test.
            // This is test-only state, restored after shutdown; no EULA or user server file is edited.
            check(net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment(),"Dedicated fixture requires a development environment");
            boolean originalDevelopment=net.minecraft.SharedConstants.IS_RUNNING_IN_IDE;
            net.minecraft.SharedConstants.IS_RUNNING_IN_IDE=true;
            try(var server=context.worldBuilder().createServer(properties);var connection=server.connect()) {
                connection.waitForChunksRender();
                server.runOnServer(s->{
                    var attacker=connection.getServerPlayer();attacker.setGameMode(GameType.SURVIVAL);
                    attacker.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(8);
                    attacker.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.MACE));
                    var profile=new GameProfile(UUID.randomUUID(),"DedicatedShield");
                    var defender=new ServerPlayer(s,s.overworld(),profile,ClientInformation.createDefault()) {
                        @Override public void tick(){super.doTick();}
                        @Override public boolean isInvulnerableTo(net.minecraft.server.level.ServerLevel level,net.minecraft.world.damagesource.DamageSource source){return false;}
                    };
                    defender.connection=new ServerGamePacketListenerImpl(s,new Connection(PacketFlow.SERVERBOUND),defender,CommonListenerCookie.createInitial(profile,false));
                    defender.setPos(attacker.getX(),attacker.getY(),attacker.getZ()+2);defender.setYRot(180);defender.setYHeadRot(180);defender.setNoGravity(true);
                    defender.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                    defender.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));defender.startUsingItem(InteractionHand.OFF_HAND);
                    attacker.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(defender)));
                    s.overworld().addNewPlayer(defender);target.set(defender);
                });
                context.waitFor(mc->mc.level.getEntity(target.get().getId()) instanceof RemotePlayer&&mc.player.getMainHandItem().is(Items.MACE)
                        &&mc.player.getAttributeBaseValue(Attributes.ATTACK_SPEED)==8);
                context.waitFor(mc->((RemotePlayer)mc.level.getEntity(target.get().getId())).getItemBlockingWith()!=null);
                context.runOnClient(mc->clock.set(new CombatTestClock()));
                for(boolean calculated:new boolean[]{true,false}) {
                    context.runOnClient(mc->{configure(mc,calculated,true);mc.player.setYRot(0);mc.player.setXRot(0);});
                    server.runOnServer(s->{var defender=target.get();defender.stopUsingItem();defender.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));defender.startUsingItem(InteractionHand.OFF_HAND);});
                    ticks(context,clock.get(),8);connection.waitForClientboundPackets();
                    for(int i=0;i<24;i++) {
                        context.runOnClient(mc->{
                            var defender=mc.level.getEntity(target.get().getId());
                            // This sends the ordinary serverbound attack and invokes the real Fabric callback.
                            // No direct DamageHud.attacked call or fabricated damage/equipment packet is used here.
                            mc.gameMode.attack(mc.player,defender);
                        });
                        connection.waitForServerboundPackets();ticks(context,clock.get(),3+i%3);
                        if(i>=5)context.runOnClient(mc->check(ShieldDamageHud.hitVisible()&&ShieldDamageHud.TRACKER.total(target.get().getUUID())>0,
                                "Dedicated-server attacks produced no shield display while combat continued: calculated="+calculated));
                    }
                    connection.waitForClientboundPackets();ticks(context,clock.get(),12);
                    int actual=server.computeOnServer(s->target.get().getOffhandItem().getDamageValue());
                    check(actual>0&&actual<Items.SHIELD.getDefaultInstance().getMaxDamage(),"Ordinary vanilla shield should take damage and survive the fight");
                    context.runOnClient(mc->{
                        double total=ShieldDamageHud.TRACKER.total(target.get().getUUID());
                        check(total>0,"Dedicated-server shield total missing");
                        if(!calculated)near(actual,total,"Reported dedicated-server total must equal real vanilla durability loss");
                        near(0,CumulativeDamageTracker.total(target.get().getUUID()),"Dedicated-server full blocks cannot count as health hits");
                    });
                    near(20,server.computeOnServer(s->(double)target.get().getHealth()),"Defender must really be blocking the client attacks");
                }
                server.runOnServer(s->s.overworld().removePlayerImmediately(target.get(),Entity.RemovalReason.DISCARDED));
            }finally{
                net.minecraft.SharedConstants.IS_RUNNING_IN_IDE=originalDevelopment;
                context.runOnClient(mc->{if(clock.get()!=null)clock.get().close();});
            }
        }finally{context.runOnClient(mc->{CombatTestClock.reset(mc);if(original.get()!=null)SylvyesPvPHud.DAMAGE_CONFIG.activate(original.get());});}
    }
}
