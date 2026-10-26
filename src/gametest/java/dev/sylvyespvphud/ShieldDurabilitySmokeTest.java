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

/** Focused client state checks plus actual vanilla server block/break packets. */
public final class ShieldDurabilitySmokeTest implements FabricClientGameTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double expected,double actual,String message){check(Math.abs(expected-actual)<.001,message+": expected "+expected+", got "+actual);}
    private static ItemStack shield(double factor){
        var stack=new ItemStack(Items.SHIELD);var b=stack.get(DataComponents.BLOCKS_ATTACKS);
        stack.set(DataComponents.BLOCKS_ATTACKS,new BlocksAttacks(0,b.disableCooldownScale(),List.of(new BlocksAttacks.DamageReduction(90,Optional.empty(),0,(float)factor)),b.itemDamage(),b.bypassedBy(),b.blockSound(),b.disableSound()));
        return stack;
    }
    private static void configure(boolean gear){
        var gson=new com.google.gson.Gson();var json=gson.toJsonTree(DamageConfig.defaults()).getAsJsonObject();
        json.addProperty("shieldTrackerEnabled",true);json.addProperty("hitShieldEnabled",true);
        json.addProperty("trackerEnabled",false);json.addProperty("hitSeconds",1);json.addProperty("calculatedDamage",true);json.addProperty("useEnemyGear",gear);
        SylvyesPvPHud.DAMAGE_CONFIG.activate(gson.fromJson(json,DamageConfig.class));
        ShieldDamageHud.reset();
    }
    private static class Target extends RemotePlayer {
        Target(net.minecraft.client.multiplayer.ClientLevel level){super(level,new GameProfile(UUID.randomUUID(),"ShieldTest"));}
        @Override public void startUsingItem(InteractionHand hand){setLivingEntityFlag(2,hand==InteractionHand.OFF_HAND);setLivingEntityFlag(1,true);}
        @Override public void stopUsingItem(){super.stopUsingItem();setLivingEntityFlag(1,false);}
    }
    private static void sound(Minecraft mc,Target target){
        var sound=target.getOffhandItem().get(DataComponents.BLOCKS_ATTACKS).blockSound().orElseThrow();
        mc.getConnection().handleSoundEvent(new ClientboundSoundPacket(sound,target.getSoundSource(),target.getX(),target.getY(),target.getZ(),1,1,0));
    }
    private static void equipment(Minecraft mc,Target target,int damage){
        ItemStack next=target.getOffhandItem().copy();next.setDamageValue(damage);
        mc.getConnection().handleSetEquipment(new ClientboundSetEquipmentPacket(target.getId(),List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND,next))));
    }
    private static void configureReported(Minecraft mc,Target target,boolean gear) {
        configure(gear);
        var gson=new com.google.gson.Gson();var json=gson.toJsonTree(SylvyesPvPHud.DAMAGE_CONFIG.current()).getAsJsonObject();
        json.addProperty("calculatedDamage",false);SylvyesPvPHud.DAMAGE_CONFIG.activate(gson.fromJson(json,DamageConfig.class));
        mc.player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.MACE));mc.player.fallDistance=3;
        target.stopUsingItem();target.setItemSlot(EquipmentSlot.OFFHAND,shield(1));target.startUsingItem(InteractionHand.OFF_HAND);
    }
    private static void attack(Minecraft mc,Target target) {mc.player.fallDistance=3;DamageHud.attacked(target);}
    private static void reportedChecks(Minecraft mc,Target target) {
        configureReported(mc,target,true);
        target.stopUsingItem();var enchanted=shield(1);enchanted.enchant(mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING),3);
        target.setItemSlot(EquipmentSlot.OFFHAND,enchanted);target.startUsingItem(InteractionHand.OFF_HAND);
        attack(mc,target);sound(mc,target);near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Reported sound must wait for measurement");
        equipment(mc,target,3);near(3,ShieldDamageHud.TRACKER.total(target.getUUID()),"Reported uses actual Unbreaking loss");near(3,DamageHud.visibleHitAmount(),"Reported hit uses measured amount without further Unbreaking division");
        equipment(mc,target,3);near(3,ShieldDamageHud.TRACKER.total(target.getUUID()),"Duplicate equipment adds nothing");
        attack(mc,target);equipment(mc,target,8);sound(mc,target);near(8,ShieldDamageHud.TRACKER.total(target.getUUID()),"Equipment before sound is counted once");
        attack(mc,target);sound(mc,target);attack(mc,target);sound(mc,target);equipment(mc,target,11);
        near(11,ShieldDamageHud.TRACKER.total(target.getUUID()),"Coalesced actual loss counts once");near(11,ShieldDamageHud.HITS.total(target.getUUID()),"Both displays receive the same measured batch");
        equipment(mc,target,12);near(11,ShieldDamageHud.TRACKER.total(target.getUUID()),"No new local attack means unrelated wear adds nothing");

        configureReported(mc,target,false);
        var weapon=mc.player.getMainHandItem();var savedShield=ShieldDamageCalculator.capture(target,weapon.getDamageSource(mc.player));
        ShieldDamageHud.prepare(target,savedShield,2,weapon,new Object(),true);sound(mc,target);equipment(mc,target,4);
        near(4,ShieldDamageHud.TRACKER.total(target.getUUID()),"Reported measurement is accepted even when predicted durability cost is zero");

        configureReported(mc,target,false);attack(mc,target);sound(mc,target);
        double fallback=ShieldDamageCalculator.capture(target,mc.player.getMainHandItem().getDamageSource(mc.player)).durability(MaceDamageCalculator.atAttack(mc.player,target),false);
        for(int i=0;i<39;i++)ShieldDamageHud.tick();near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Reported waits full 40 ticks");
        ShieldDamageHud.tick();near(fallback,ShieldDamageHud.TRACKER.total(target.getUUID()),"Missing data uses calculated fallback");
        for(int i=0;i<5;i++)ShieldDamageHud.tick();equipment(mc,target,2);
        near(2,ShieldDamageHud.TRACKER.total(target.getUUID()),"Late measurement replaces fallback");near(2,DamageHud.visibleHitAmount(),"Late measurement corrects hit field");
        for(int i=0;i<15;i++)ShieldDamageHud.tick();check(!ShieldDamageHud.hitVisible(),"Correction must not extend display timer");

        configureReported(mc,target,false);attack(mc,target);sound(mc,target);
        for(int i=0;i<60;i++)ShieldDamageHud.tick();check(!ShieldDamageHud.hitVisible(),"Fallback hit expires");
        equipment(mc,target,3);near(3,ShieldDamageHud.TRACKER.total(target.getUUID()),"Correction survives hit expiry while tracker storage is active");check(!ShieldDamageHud.hitVisible(),"Correction cannot resurrect expired hit display");

        configureReported(mc,target,false);attack(mc,target);sound(mc,target);
        for(int i=0;i<40;i++)ShieldDamageHud.tick();ShieldDamageHud.healthHit(target.getUUID());equipment(mc,target,2);
        near(2,ShieldDamageHud.TRACKER.total(target.getUUID()),"Health-hit reset does not lose tracker correction");check(!ShieldDamageHud.hitVisible(),"Late correction cannot revive health-reset streak");

        configureReported(mc,target,false);target.getOffhandItem().setDamageValue(10);attack(mc,target);sound(mc,target);equipment(mc,target,5);
        for(int i=0;i<41;i++)ShieldDamageHud.tick();near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Repair is not damage and cancels stale fallback");
        configureReported(mc,target,false);attack(mc,target);sound(mc,target);
        var replacement=shield(1);replacement.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Replacement"));
        mc.getConnection().handleSetEquipment(new ClientboundSetEquipmentPacket(target.getId(),List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND,replacement))));
        for(int i=0;i<41;i++)ShieldDamageHud.tick();near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Shield swap cancels old pending measurement");

        configureReported(mc,target,false);target.getOffhandItem().setDamageValue(target.getOffhandItem().getMaxDamage()-2);attack(mc,target);sound(mc,target);
        mc.getConnection().handleEntityEvent(new ClientboundEntityEventPacket(target,(byte)48));
        near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Reported break resets storage");near(2,ShieldDamageHud.TRACKER.amount(),"Reported break uses server-known remaining durability");near(2,DamageHud.visibleHitAmount(),"Reported break final hit amount");
        mc.getConnection().handleSetEquipment(new ClientboundSetEquipmentPacket(target.getId(),List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND,ItemStack.EMPTY))));
        near(2,ShieldDamageHud.TRACKER.amount(),"Break and removal cannot double count");
        target.stopUsingItem();target.setItemSlot(EquipmentSlot.OFFHAND,shield(1));target.startUsingItem(InteractionHand.OFF_HAND);attack(mc,target);equipment(mc,target,4);
        near(4,ShieldDamageHud.TRACKER.total(target.getUUID()),"Replacement shield starts a fresh measured sum");
        configureReported(mc,target,false);target.getOffhandItem().setDamageValue(target.getOffhandItem().getMaxDamage()-3);attack(mc,target);sound(mc,target);
        mc.getConnection().handleSetEquipment(new ClientboundSetEquipmentPacket(target.getId(),List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND,ItemStack.EMPTY))));
        mc.getConnection().handleEntityEvent(new ClientboundEntityEventPacket(target,(byte)48));near(3,ShieldDamageHud.TRACKER.amount(),"Removal-before-break retains server durability baseline");
    }
    @Override public void runTest(ClientGameTestContext context){
        var original=new AtomicReference<DamageConfig>();
        try(var world=context.worldBuilder().create()){
            world.getConnection().waitForChunksRender();
            context.runOnClient(mc->{
                original.set(SylvyesPvPHud.DAMAGE_CONFIG.current());DamageHud.tick(mc);configure(false);
                var p=mc.player;p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.MACE));p.setSprinting(true);p.fallDistance=3;AttributeSwaps.endTick();
                var target=new Target(mc.level);target.setId(2000020);target.setPos(p.getX(),p.getY(),p.getZ()+2);target.setYHeadRot(180);
                target.setItemSlot(EquipmentSlot.OFFHAND,shield(1));target.startUsingItem(InteractionHand.OFF_HAND);mc.level.addEntity(target);
                var snapshot=ShieldDamageCalculator.capture(target,p.getMainHandItem().getDamageSource(p));
                near(11,snapshot.durability(10,false),"Vanilla shield durability function");
                near(0,snapshot.durability(2,false),"Below durability threshold");
                var enchanted=shield(1);enchanted.enchant(mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING),3);
                target.stopUsingItem();target.setItemSlot(EquipmentSlot.OFFHAND,enchanted);target.startUsingItem(InteractionHand.OFF_HAND);
                near(11/4.0,ShieldDamageCalculator.capture(target,p.getMainHandItem().getDamageSource(p)).durability(10,true),"Visible Unbreaking mean");
                target.stopUsingItem();target.setItemSlot(EquipmentSlot.OFFHAND,shield(1));target.startUsingItem(InteractionHand.OFF_HAND);
                double raw=MaceDamageCalculator.atAttack(p,target);double cost=ShieldDamageCalculator.capture(target,p.getMainHandItem().getDamageSource(p)).durability(raw,false);check(cost>0,"Mace cost must be positive");
                DamageHud.attacked(target);near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Unconfirmed attack");sound(mc,target);
                near(cost,ShieldDamageHud.TRACKER.total(target.getUUID()),"Block sound must confirm shield damage");near(cost,DamageHud.visibleHitAmount(),"Hit shield field");
                equipment(mc,target,(int)cost);near(cost,ShieldDamageHud.TRACKER.total(target.getUUID()),"Sound plus equipment must not double count");
                DamageHud.attacked(target);equipment(mc,target,(int)cost*2);sound(mc,target);
                near(cost*2,ShieldDamageHud.TRACKER.total(target.getUUID()),"Equipment before sound must not double count");
                DamageHud.attacked(target);DamageHud.attacked(target);equipment(mc,target,(int)cost*4);
                near(cost*4,ShieldDamageHud.TRACKER.total(target.getUUID()),"Coalesced silent spam hits");
                for(int i=0;i<19;i++)ShieldDamageHud.tick();
                DamageHud.attacked(target);equipment(mc,target,(int)cost*5);
                near(cost*5,DamageHud.visibleHitAmount(),"Spam refreshes hit sum");
                for(int i=0;i<20;i++)ShieldDamageHud.tick();check(!ShieldDamageHud.hitVisible(),"Hit sum times out");
                DamageHud.attacked(target);equipment(mc,target,(int)cost*6);near(cost,DamageHud.visibleHitAmount(),"New streak after timeout");
                DamageHud.attacked(target);mc.getConnection().handleEntityEvent(new ClientboundEntityEventPacket(target,(byte)48));
                near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Break resets stored tracker sum");near(cost*7,ShieldDamageHud.TRACKER.amount(),"Break retains final tracker number");near(cost*2,DamageHud.visibleHitAmount(),"Break retains final hit number");
                target.stopUsingItem();target.setItemSlot(EquipmentSlot.OFFHAND,shield(1));target.startUsingItem(InteractionHand.OFF_HAND);DamageHud.attacked(target);equipment(mc,target,(int)cost);
                near(cost,ShieldDamageHud.TRACKER.total(target.getUUID()),"Replacement shield first silent hit starts a fresh total");near(cost,DamageHud.visibleHitAmount(),"Replacement shield first hit starts a fresh streak");
                target.stopUsingItem();DamageHud.attacked(target);DamageHud.damageEvent(new ClientboundDamageEventPacket(target,p.getMainHandItem().getDamageSource(p)));DamageHud.tick(mc);
                check(!ShieldDamageHud.hitVisible(),"Health damage ends shield streak");
                p.fallDistance=3;target.setItemSlot(EquipmentSlot.OFFHAND,shield(.5));target.startUsingItem(InteractionHand.OFF_HAND);
                DamageHud.attacked(target);sound(mc,target);DamageHud.tick(mc);check(!ShieldDamageHud.hitVisible(),"Partial block prioritizes health");check(ShieldDamageHud.TRACKER.total(target.getUUID())>0,"Partial block contributes shield cost");
                ShieldDamageHud.reset();
                p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));DamageHud.attacked(target);sound(mc,target);near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Swords excluded");
                configure(false);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SPEAR));p.setXRot(0);p.setYRot(0);p.setDeltaMovement(0,0,3);p.fallDistance=0;
                target.stopUsingItem();target.setItemSlot(EquipmentSlot.OFFHAND,shield(1));target.startUsingItem(InteractionHand.OFF_HAND);
                DamageHud.spearJab(p.getMainHandItem().get(DataComponents.PIERCING_WEAPON));sound(mc,target);
                check(ShieldDamageHud.TRACKER.total(target.getUUID())>0,"Spear jab shield confirmation");
                ShieldDamageHud.reset();
                var weapon=p.getMainHandItem();var kinetic=weapon.get(DataComponents.KINETIC_WEAPON);
                weapon.set(DataComponents.KINETIC_WEAPON,new net.minecraft.world.item.component.KineticWeapon(kinetic.contactCooldownTicks(),0,kinetic.dismountConditions(),kinetic.knockbackConditions(),Optional.of(new net.minecraft.world.item.component.KineticWeapon.Condition(1000,0,0)),kinetic.forwardMovement(),kinetic.damageMultiplier(),kinetic.sound(),kinetic.hitSound()));
                double base=p.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(10);
                p.startUsingItem(InteractionHand.MAIN_HAND);DamageHud.tick(mc);sound(mc,target);
                check(ShieldDamageHud.TRACKER.total(target.getUUID())>0,"Spear charge shield confirmation");p.stopUsingItem();p.setDeltaMovement(0,0,0);p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(base);
                ShieldDamageHud.reset();p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.MACE));p.fallDistance=3;
                DamageHud.attacked(target);DamageHud.damageEvent(new ClientboundDamageEventPacket(target,p.getMainHandItem().getDamageSource(target)));equipment(mc,target,40);
                near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Other attacker must invalidate unmatched local candidate");
                for(int i=0;i<10;i++)DamageHud.tick(mc);
                ShieldDamageHud.reset();var gson=new com.google.gson.Gson();var reported=gson.toJsonTree(SylvyesPvPHud.DAMAGE_CONFIG.current()).getAsJsonObject();reported.addProperty("calculatedDamage",false);SylvyesPvPHud.DAMAGE_CONFIG.activate(gson.fromJson(reported,DamageConfig.class));
                target.stopUsingItem();p.fallDistance=3;DamageHud.attacked(target);DamageHud.damageEvent(new ClientboundDamageEventPacket(target,p.getMainHandItem().getDamageSource(p)));
                p.fallDistance=3;target.setItemSlot(EquipmentSlot.OFFHAND,shield(1));target.startUsingItem(InteractionHand.OFF_HAND);DamageHud.attacked(target);sound(mc,target);
                equipment(mc,target,3);
                double newShieldSum=ShieldDamageHud.HITS.total(target.getUUID());check(newShieldSum>0,"Shield streak after reported health hit");
                for(int i=0;i<10;i++)DamageHud.tick(mc);
                near(newShieldSum,ShieldDamageHud.HITS.total(target.getUUID()),"Delayed health result must not clear a newer shield streak");check(ShieldDamageHud.hitVisible(),"Delayed health result must not replace a newer shield display");
                reportedChecks(mc,target);
                mc.level.removeEntity(target.getId(),Entity.RemovalReason.DISCARDED);
                var draft=new SettingsSession();var style=draft.hud.shieldTracker();check(HudPreview.centered(style)!=null,"Shield HUD preview style");
            });
            context.setScreen(()->new HudPlacementScreen(new SettingsScreen(null),new SettingsSession(),9));
            context.waitTicks(2);
            context.runOnClient(mc->{var screen=(HudPlacementScreen)mc.gui.screen();check(screen.currentBounds()!=null&&screen.currentBounds().width()>0,"Shield HUD placement preview must render");});
            context.takeScreenshot("shield-tracker-placement");
            context.runOnClient(mc->mc.gui.setScreen(null));
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
        }finally{context.runOnClient(mc->{ShieldDamageHud.reset();if(original.get()!=null)SylvyesPvPHud.DAMAGE_CONFIG.activate(original.get());});}
    }
}
