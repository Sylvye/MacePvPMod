package dev.sylvyespvphud;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.enchantment.Enchantments;

/** Durability measurement, late corrections, and actual vanilla server packets. */
public final class ShieldDurabilitySmokeTest implements FabricClientGameTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double expected,double actual,String message){check(Math.abs(expected-actual)<.001,message+": expected "+expected+", got "+actual);}
    private static ItemStack shield(double factor){return ShieldDamageSmokeTest.shield((float)factor);}
    private static void configure(boolean gear) {
        var mc=Minecraft.getInstance();CombatTestClock.reset(mc);
        var gson=new com.google.gson.Gson();var json=gson.toJsonTree(DamageConfig.defaults()).getAsJsonObject();
        json.addProperty("shieldTrackerEnabled",true);json.addProperty("hitShieldEnabled",true);
        json.addProperty("trackerEnabled",false);json.addProperty("hitSeconds",1);json.addProperty("calculatedDamage",true);json.addProperty("useEnemyGear",gear);
        SylvyesPvPHud.DAMAGE_CONFIG.activate(gson.fromJson(json,DamageConfig.class));
    }
    private static void reported(Minecraft mc,ShieldDamageSmokeTest.Target target) {
        configure(false);var gson=new com.google.gson.Gson();var json=gson.toJsonTree(SylvyesPvPHud.DAMAGE_CONFIG.current()).getAsJsonObject();
        json.addProperty("calculatedDamage",false);SylvyesPvPHud.DAMAGE_CONFIG.activate(gson.fromJson(json,DamageConfig.class));
        ShieldDamageSmokeTest.fresh(target);mc.player.fallDistance=3;
    }
    private static void attack(Minecraft mc,ShieldDamageSmokeTest.Target target){mc.player.fallDistance=3;DamageHud.attacked(target);}
    private static void sound(Minecraft mc,ShieldDamageSmokeTest.Target target){ShieldDamageSmokeTest.sound(mc,target);}
    private static void equipment(Minecraft mc,ShieldDamageSmokeTest.Target target,int damage){ShieldDamageSmokeTest.equip(mc,target,damage);}
    @Override public void runTest(ClientGameTestContext context) {
        var original=new AtomicReference<DamageConfig>();
        try(var world=context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(mc->{
              original.set(SylvyesPvPHud.DAMAGE_CONFIG.current());DamageHud.tick(mc);
              try(var clock=new CombatTestClock()) {
                var target=new ShieldDamageSmokeTest.Target(mc.level);target.setId(2000020);
                target.setPos(mc.player.getX(),mc.player.getY(),mc.player.getZ()+2);mc.level.addEntity(target);
                reported(mc,target);
                var enchanted=shield(1);enchanted.enchant(mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING),3);
                target.setItemSlot(EquipmentSlot.OFFHAND,enchanted);
                attack(mc,target);sound(mc,target);clock.settle(mc);
                near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Reported sound waits for measurement");
                equipment(mc,target,3);clock.settle(mc);
                near(3,ShieldDamageHud.TRACKER.total(target.getUUID()),"Actual Unbreaking wear is not divided twice");
                near(3,DamageHud.visibleHitAmount(),"Reported hit displays measured wear");
                equipment(mc,target,3);clock.settle(mc);near(3,ShieldDamageHud.TRACKER.total(target.getUUID()),"Duplicate equipment adds nothing");
                attack(mc,target);equipment(mc,target,8);sound(mc,target);clock.settle(mc);
                near(8,ShieldDamageHud.TRACKER.total(target.getUUID()),"Equipment before sound counts once");
                attack(mc,target);sound(mc,target);attack(mc,target);sound(mc,target);equipment(mc,target,11);clock.settle(mc);
                near(11,ShieldDamageHud.TRACKER.total(target.getUUID()),"Coalesced reported wear counts once");
                equipment(mc,target,12);clock.settle(mc);near(11,ShieldDamageHud.TRACKER.total(target.getUUID()),"Unmatched wear adds nothing");

                reported(mc,target);attack(mc,target);sound(mc,target);
                double fallback=ShieldDamageCalculator.potential(target,mc.player.getMainHandItem().getDamageSource(mc.player)).getFirst().confirmed()
                        .durability(MaceDamageCalculator.atAttack(mc.player,target),false);
                clock.advance(1999);DamageHud.tick(mc);near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Reported waits full retention window");
                clock.advance(1);DamageHud.tick(mc);near(fallback,ShieldDamageHud.TRACKER.total(target.getUUID()),"Missing wear falls back after confirmation window");
                for(int i=0;i<5;i++)clock.tick(mc);equipment(mc,target,2);
                near(2,ShieldDamageHud.TRACKER.total(target.getUUID()),"Late measurement replaces fallback");near(2,DamageHud.visibleHitAmount(),"Late correction adjusts existing display");
                for(int i=0;i<15;i++)clock.tick(mc);check(!ShieldDamageHud.hitVisible(),"Correction does not refresh display timer");
                reported(mc,target);attack(mc,target);sound(mc,target);clock.settle(mc);clock.advance(1500);DamageHud.tick(mc);
                ShieldDamageHud.healthHit(target.getUUID());equipment(mc,target,2);
                near(2,ShieldDamageHud.TRACKER.total(target.getUUID()),"Health display reset preserves tracker correction");check(!ShieldDamageHud.hitVisible(),"Correction cannot revive dismissed streak");

                reported(mc,target);target.getOffhandItem().setDamageValue(10);attack(mc,target);sound(mc,target);equipment(mc,target,5);clock.advance(2500);DamageHud.tick(mc);
                near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Repair cancels stale fallback");
                reported(mc,target);attack(mc,target);sound(mc,target);
                var replacement=shield(1);replacement.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Replacement"));
                mc.getConnection().handleSetEquipment(new ClientboundSetEquipmentPacket(target.getId(),List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND,replacement))));
                clock.advance(2500);DamageHud.tick(mc);near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Shield swap cancels stale fallback");

                for(boolean removalFirst:new boolean[]{false,true}) {
                    reported(mc,target);target.getOffhandItem().setDamageValue(target.getOffhandItem().getMaxDamage()-2);attack(mc,target);sound(mc,target);
                    if(removalFirst)mc.getConnection().handleSetEquipment(new ClientboundSetEquipmentPacket(target.getId(),List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND,ItemStack.EMPTY))));
                    mc.getConnection().handleEntityEvent(new ClientboundEntityEventPacket(target,(byte)48));
                    if(!removalFirst)mc.getConnection().handleSetEquipment(new ClientboundSetEquipmentPacket(target.getId(),List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND,ItemStack.EMPTY))));
                    clock.settle(mc);near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Break resets stored total");near(2,ShieldDamageHud.TRACKER.amount(),"Break retains measured remaining durability");
                    target.setItemSlot(EquipmentSlot.OFFHAND,shield(1));attack(mc,target);equipment(mc,target,4);clock.settle(mc);
                    near(4,ShieldDamageHud.TRACKER.total(target.getUUID()),"Replacement shield starts a fresh total");
                }
                configure(false);ShieldDamageSmokeTest.fresh(target);mc.player.fallDistance=3;
                double raw=MaceDamageCalculator.atAttack(mc.player,target);
                double cost=ShieldDamageCalculator.potential(target,mc.player.getMainHandItem().getDamageSource(mc.player)).getFirst().confirmed().durability(raw,false);
                attack(mc,target);attack(mc,target);equipment(mc,target,(int)(cost*2));clock.settle(mc);
                near(cost*2,ShieldDamageHud.TRACKER.total(target.getUUID()),"Identical silent blocks can share one equipment update");

                configure(true);ShieldDamageSmokeTest.fresh(target);target.setItemSlot(EquipmentSlot.OFFHAND,shield(.5));
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(20);
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
                mc.player.fallDistance=3;raw=MaceDamageCalculator.atAttack(mc.player,target);
                double expected=MaceDamageCalculator.afterGear(raw*.5,mc.player.getMainHandItem(),target);
                attack(mc,target);target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);sound(mc,target);clock.settle(mc);
                near(expected,DamageHud.visibleHitAmount(),"Partial block applies snapshotted gear after shield reduction");
                mc.level.removeEntity(target.getId(),Entity.RemovalReason.DISCARDED);
                CombatTestClock.reset(mc);
              }
            });
            // A real server player supplies vanilla onBlocked sounds, item damage sync, and break events.
            var serverTarget=new AtomicReference<ServerPlayer>();
            world.getServer().runOnServer(server->{
                var player=server.getPlayerList().getPlayers().getFirst();var profile=new GameProfile(UUID.randomUUID(),"ServerShield");
                var target=new ServerPlayer(server,server.overworld(),profile,ClientInformation.createDefault()){
                    @Override public void tick(){super.doTick();}
                    @Override public boolean isInvulnerableTo(net.minecraft.server.level.ServerLevel level,net.minecraft.world.damagesource.DamageSource source){return false;}
                };
                target.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),target,CommonListenerCookie.createInitial(profile,false));
                target.setPos(player.getX(),player.getY(),player.getZ()+2);target.setYRot(180);target.setYHeadRot(180);target.setNoGravity(true);
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                target.setItemSlot(EquipmentSlot.OFFHAND,shield(1));target.startUsingItem(InteractionHand.OFF_HAND);
                player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(target)));
                server.overworld().addNewPlayer(target);serverTarget.set(target);
            });
            context.waitFor(mc->mc.level.getEntity(serverTarget.get().getId()) instanceof RemotePlayer);
            context.waitFor(mc->{var e=(RemotePlayer)mc.level.getEntity(serverTarget.get().getId());return e.getItemBlockingWith()!=null;});
            var rawDamage=new AtomicReference<Double>();
            context.runOnClient(mc->{
                configure(false);var g=new com.google.gson.Gson();var j=g.toJsonTree(SylvyesPvPHud.DAMAGE_CONFIG.current()).getAsJsonObject();j.addProperty("calculatedDamage",false);SylvyesPvPHud.DAMAGE_CONFIG.activate(g.fromJson(j,DamageConfig.class));mc.player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.MACE));mc.player.fallDistance=3;mc.player.setSprinting(true);AttributeSwaps.endTick();
                var target=(RemotePlayer)mc.level.getEntity(serverTarget.get().getId());rawDamage.set(MaceDamageCalculator.atAttack(mc.player,target));check(ShieldDamageCalculator.capture(target,mc.player.getMainHandItem().getDamageSource(mc.player)).durability(rawDamage.get(),false)>0,"Real target client snapshot must block");DamageHud.attacked(target);
            });
            world.getServer().runOnServer(server->{var attacker=server.getPlayerList().getPlayers().getFirst();attacker.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.MACE));serverTarget.get().hurtServer(server.overworld(),attacker.getMainHandItem().getDamageSource(attacker),rawDamage.get().floatValue());check(serverTarget.get().getOffhandItem().getDamageValue()>0,"Vanilla server must damage shield");serverTarget.get().getOffhandItem().setDamageValue(2);});
            context.waitFor(mc->ShieldDamageHud.TRACKER.total(serverTarget.get().getUUID())>0);
            context.runOnClient(mc->{check(ShieldDamageHud.hitVisible(),"Actual vanilla block did not update hit display");near(2,ShieldDamageHud.TRACKER.total(serverTarget.get().getUUID()),"Real server durability adjustment must override attack estimate");});
            // Confirm a rapid silent block using the durability update alone.
            double previous=context.computeOnClient(mc->ShieldDamageHud.TRACKER.total(serverTarget.get().getUUID()));
            context.runOnClient(mc->{mc.player.fallDistance=3;var target=(RemotePlayer)mc.level.getEntity(serverTarget.get().getId());rawDamage.set(MaceDamageCalculator.atAttack(mc.player,target));DamageHud.attacked(target);});
            world.getServer().runOnServer(server->{var attacker=server.getPlayerList().getPlayers().getFirst();var target=serverTarget.get();target.invulnerableTime=20;target.hurtServer(server.overworld(),attacker.getMainHandItem().getDamageSource(attacker),rawDamage.get().floatValue());});
            context.waitFor(mc->ShieldDamageHud.TRACKER.total(serverTarget.get().getUUID())>previous);
            // Spear jabs and charges use their own capture paths, confirmed by real equipment packets.
            double beforeJab=context.computeOnClient(mc->ShieldDamageHud.TRACKER.total(serverTarget.get().getUUID()));
            context.runOnClient(mc->{
                mc.player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SPEAR));mc.player.setYRot(0);mc.player.setXRot(0);
                rawDamage.set(MaceDamageCalculator.effectiveAttackDamage(mc.player));DamageHud.spearJab(mc.player.getMainHandItem().get(DataComponents.PIERCING_WEAPON));
            });
            world.getServer().runOnServer(server->{var attacker=server.getPlayerList().getPlayers().getFirst();attacker.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SPEAR));var target=serverTarget.get();target.invulnerableTime=20;target.hurtServer(server.overworld(),attacker.getMainHandItem().getDamageSource(attacker),rawDamage.get().floatValue());});
            context.waitFor(mc->ShieldDamageHud.TRACKER.total(serverTarget.get().getUUID())>beforeJab);
            double beforeCharge=context.computeOnClient(mc->ShieldDamageHud.TRACKER.total(serverTarget.get().getUUID()));
            context.runOnClient(mc->{
                var weapon=mc.player.getMainHandItem();var kinetic=weapon.get(DataComponents.KINETIC_WEAPON);
                weapon.set(DataComponents.KINETIC_WEAPON,new net.minecraft.world.item.component.KineticWeapon(kinetic.contactCooldownTicks(),0,kinetic.dismountConditions(),kinetic.knockbackConditions(),Optional.of(new net.minecraft.world.item.component.KineticWeapon.Condition(1000,0,0)),kinetic.forwardMovement(),kinetic.damageMultiplier(),kinetic.sound(),kinetic.hitSound()));
                mc.player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(10);rawDamage.set(10.0);
                mc.player.startUsingItem(InteractionHand.MAIN_HAND);DamageHud.tick(mc);mc.player.stopUsingItem();mc.player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(1);
                check(DamageHud.HISTORY.entries().stream().anyMatch(e->e.value.target.getId()==serverTarget.get().getId()&&e.value.type==DamageWeapon.SPEAR),"Charge fixture must have predicted contact");
            });
            world.getServer().runOnServer(server->{var attacker=server.getPlayerList().getPlayers().getFirst();var target=serverTarget.get();target.invulnerableTime=20;target.hurtServer(server.overworld(),attacker.getMainHandItem().getDamageSource(attacker),rawDamage.get().floatValue());});
            context.waitFor(mc->ShieldDamageHud.TRACKER.total(serverTarget.get().getUUID())>beforeCharge);
            context.runOnClient(mc->mc.player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.MACE)));
            world.getServer().runOnServer(server->server.getPlayerList().getPlayers().getFirst().setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.MACE)));
            // Break a nearly depleted shield and preserve the final number after clearing storage.
            world.getServer().runOnServer(server->{var target=serverTarget.get();target.getOffhandItem().setDamageValue(target.getOffhandItem().getMaxDamage()-1);});
            context.waitFor(mc->{var target=(RemotePlayer)mc.level.getEntity(serverTarget.get().getId());return target.getOffhandItem().getDamageValue()==target.getOffhandItem().getMaxDamage()-1;});
            context.runOnClient(mc->{mc.player.fallDistance=3;var target=(RemotePlayer)mc.level.getEntity(serverTarget.get().getId());rawDamage.set(MaceDamageCalculator.atAttack(mc.player,target));DamageHud.attacked(target);});
            world.getServer().runOnServer(server->{var attacker=server.getPlayerList().getPlayers().getFirst();var target=serverTarget.get();attacker.setPos(target.getX(),target.getY(),target.getZ()-2);target.invulnerableTime=0;var source=attacker.getMainHandItem().getDamageSource(attacker);check(ShieldDamageCalculator.capture(target,source).remaining(rawDamage.get())==0,"Server break attack must face the active shield");target.hurtServer(server.overworld(),source,rawDamage.get().floatValue());check(target.getOffhandItem().isEmpty(),"Server shield should break: raw="+rawDamage.get()+", damage="+target.getOffhandItem().getDamageValue()+", blocking="+(target.getItemBlockingWith()!=null)+", facing="+target.getYHeadRot());});
            context.waitFor(mc->ShieldDamageHud.TRACKER.total(serverTarget.get().getUUID())==0);
            context.runOnClient(mc->check(ShieldDamageHud.TRACKER.amount()>previous,"Actual break must retain final total"));
            world.getServer().runOnServer(server->{server.overworld().removePlayerImmediately(serverTarget.get(),Entity.RemovalReason.DISCARDED);});
        }finally{context.runOnClient(mc->{ShieldDamageHud.reset();DamageHud.clearPending();if(original.get()!=null)SylvyesPvPHud.DAMAGE_CONFIG.activate(original.get());});}
    }
}
