package dev.sylvyespvphud;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;

/** Delayed and contradictory evidence through the actual client packet hooks. */
public final class ShieldDamageSmokeTest implements FabricClientGameTest {
    static final class Target extends RemotePlayer {
        Target(net.minecraft.client.multiplayer.ClientLevel level){super(level,new GameProfile(UUID.randomUUID(),"LagShield"));}
        @Override public void startUsingItem(InteractionHand hand){setLivingEntityFlag(2,hand==InteractionHand.OFF_HAND);setLivingEntityFlag(1,true);}
        @Override public void stopUsingItem(){super.stopUsingItem();setLivingEntityFlag(1,false);}
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void near(double expected,double actual,String message){check(Math.abs(expected-actual)<.001,message+": expected "+expected+", got "+actual);}
    static ItemStack shield(float factor) {
        var item=new ItemStack(Items.SHIELD);var b=item.get(DataComponents.BLOCKS_ATTACKS);
        item.set(DataComponents.BLOCKS_ATTACKS,new BlocksAttacks(0,b.disableCooldownScale(),List.of(new BlocksAttacks.DamageReduction(90,Optional.empty(),0,factor)),b.itemDamage(),b.bypassedBy(),b.blockSound(),b.disableSound()));
        return item;
    }
    static void configure(Minecraft mc,boolean calculated,boolean displays) {
        CombatTestClock.reset(mc);var gson=new com.google.gson.Gson();var json=gson.toJsonTree(DamageConfig.defaults()).getAsJsonObject();
        json.addProperty("calculatedDamage",calculated);json.addProperty("shieldTrackerEnabled",displays);json.addProperty("hitShieldEnabled",displays);
        json.addProperty("trackerEnabled",true);json.addProperty("hitSeconds",1);json.addProperty("trackerSeconds",10);
        SylvyesPvPHud.DAMAGE_CONFIG.activate(gson.fromJson(json,DamageConfig.class));
    }
    static void equip(Minecraft mc,Target target,int damage) {
        var next=target.getOffhandItem().copy();next.setDamageValue(damage);
        mc.getConnection().handleSetEquipment(new ClientboundSetEquipmentPacket(target.getId(),List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND,next))));
    }
    static void sound(Minecraft mc,Target target) {
        var sound=target.getOffhandItem().get(DataComponents.BLOCKS_ATTACKS).blockSound().orElseThrow();
        mc.getConnection().handleSoundEvent(new ClientboundSoundPacket(sound,target.getSoundSource(),target.getX(),target.getY(),target.getZ(),1,1,0));
    }
    static void health(Minecraft mc,Target target) {
        mc.getConnection().handleDamageEvent(new ClientboundDamageEventPacket(target,mc.player.getMainHandItem().getDamageSource(mc.player)));
    }
    static void fresh(Target target) {
        target.stopUsingItem();target.setHealth(20);target.setItemSlot(EquipmentSlot.OFFHAND,shield(1));target.setYHeadRot(180);
    }
    @Override public void runTest(ClientGameTestContext context) {
        var original=new java.util.concurrent.atomic.AtomicReference<DamageConfig>();
        try(var world=context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(mc->{
              original.set(SylvyesPvPHud.DAMAGE_CONFIG.current());
              try(var clock=new CombatTestClock()) {
                DamageHud.tick(mc);var p=mc.player;var target=new Target(mc.level);target.setId(2000010);
                target.setPos(p.getX(),p.getY(),p.getZ()+2);mc.level.addEntity(target);
                var other=new Target(mc.level);other.setId(2000011);other.setPos(p.getX()+4,p.getY(),p.getZ());mc.level.addEntity(other);
                for(int delay:new int[]{100,300,800})for(boolean displays:new boolean[]{false,true}) {
                    configure(mc,true,displays);fresh(target);p.fallDistance=8;
                    double raw=MaceDamageCalculator.atAttack(p,target);DamageHud.attacked(target);
                    var oldPosition=target.position();clock.advance(delay);
                    target.setPos(oldPosition.add(3,0,0)); // Sound still refers to the earlier server position.
                    var sound=target.getOffhandItem().get(DataComponents.BLOCKS_ATTACKS).blockSound().orElseThrow();
                    mc.getConnection().handleSoundEvent(new ClientboundSoundPacket(sound,target.getSoundSource(),oldPosition.x,oldPosition.y,oldPosition.z,1,1,0));
                    check(DamageHud.visibleHit().isEmpty(),"Block must wait for reconciliation");
                    clock.settle(mc);
                    near(0,CumulativeDamageTracker.total(target.getUUID()),"Quick raised shield counted as health damage");
                    near(displays?Math.floor(raw)+1:0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Delayed positional block confirmation");
                    check(displays||DamageHud.visibleHit().isEmpty(),"Disabled shield display leaked a health hit");
                    target.setPos(oldPosition);
                }
                configure(mc,true,true);fresh(target);p.fallDistance=3;DamageHud.attacked(target);
                var rendered=target.position();var intermediate=rendered.add(2,0,0);
                target.setPos(intermediate);var middlePacket=net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket.of(target);
                target.setPos(rendered.add(4,0,0));var latestPacket=net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket.of(target);
                target.setPos(rendered);mc.getConnection().handleEntityPositionSync(middlePacket);mc.getConnection().handleEntityPositionSync(latestPacket);
                target.setPos(rendered); // Rendering can trail several server position updates.
                var movementSound=target.getOffhandItem().get(DataComponents.BLOCKS_ATTACKS).blockSound().orElseThrow();
                mc.getConnection().handleSoundEvent(new ClientboundSoundPacket(movementSound,target.getSoundSource(),intermediate.x,intermediate.y,intermediate.z,1,1,0));
                clock.settle(mc);check(ShieldDamageHud.hitVisible(),"Intermediate server positions must survive packet coalescing and interpolation");
                target.syncPacketPositionCodec(rendered.x,rendered.y,rendered.z);
                configure(mc,true,true);fresh(target);p.fallDistance=8;double savedSmash=MaceDamageCalculator.atAttack(p,target);DamageHud.attacked(target);
                clock.advance(50);p.fallDistance=0;DamageHud.attacked(target);clock.advance(300);health(mc,target);clock.settle(mc);
                near(savedSmash,DamageHud.visibleHitAmount(),"Weak follow-up must not replace pending smash");
                near(savedSmash,CumulativeDamageTracker.total(target.getUUID()),"Confirmed smash must not be suppressed by a weak follow-up");
                health(mc,target);clock.settle(mc);check(CumulativeDamageTracker.total(target.getUUID())>20,"Two ordered confirmations should resolve both saved attacks");

                for(boolean equipmentFirst:new boolean[]{false,true}) {
                    configure(mc,true,true);fresh(target);p.fallDistance=3;double raw=MaceDamageCalculator.atAttack(p,target);
                    DamageHud.attacked(target);clock.advance(300);
                    if(equipmentFirst){equip(mc,target,(int)(Math.floor(raw)+1));sound(mc,target);}else{sound(mc,target);equip(mc,target,(int)(Math.floor(raw)+1));}
                    clock.settle(mc);near(Math.floor(raw)+1,ShieldDamageHud.TRACKER.total(target.getUUID()),"Sound and equipment count once");
                    near(0,CumulativeDamageTracker.total(target.getUUID()),"Full block cannot add health damage");
                }
                configure(mc,true,true);fresh(target);p.fallDistance=3;DamageHud.attacked(target);health(mc,target);sound(mc,target);clock.settle(mc);
                check(DamageHud.visibleHit().isEmpty(),"Contradictory health/block evidence must stay uncounted");
                near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Conflicting evidence changed shield total");

                configure(mc,true,true);fresh(target);p.fallDistance=3;double raw=MaceDamageCalculator.atAttack(p,target);
                target.setItemSlot(EquipmentSlot.OFFHAND,shield(.5f));DamageHud.attacked(target);sound(mc,target);clock.settle(mc);
                near(raw*.5,CumulativeDamageTracker.total(target.getUUID()),"Partial block must contribute health damage once");
                check(ShieldDamageHud.TRACKER.total(target.getUUID())>0,"Partial block must contribute shield wear");
                check(!ShieldDamageHud.hitVisible(),"Partial block prioritizes health display");

                configure(mc,true,true);fresh(target);p.fallDistance=3;DamageHud.attacked(target);clock.advance(100);
                target.startUsingItem(InteractionHand.OFF_HAND);target.stopUsingItem();sound(mc,target);clock.settle(mc);
                check(ShieldDamageHud.hitVisible(),"Lowered shield must preserve the attack-time item snapshot");
                configure(mc,true,true);fresh(target);target.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));
                target.startUsingItem(InteractionHand.OFF_HAND);p.fallDistance=3;
                near(10,ShieldDamageCalculator.capture(target,p.getMainHandItem().getDamageSource(p)).remaining(10),"Activation delay must remain part of prediction");
                DamageHud.attacked(target);clock.advance(300);sound(mc,target);clock.settle(mc);
                check(ShieldDamageHud.hitVisible(),"Server block overrides delayed activation flags");

                configure(mc,true,true);fresh(target);target.startUsingItem(InteractionHand.OFF_HAND);p.fallDistance=3;
                raw=MaceDamageCalculator.atAttack(p,target);DamageHud.attacked(target);target.stopUsingItem();health(mc,target);clock.settle(mc);
                near(raw,CumulativeDamageTracker.total(target.getUUID()),"Server health confirmation overrides stale active shield prediction");

                configure(mc,true,true);fresh(target);fresh(other);other.setPos(target.position());p.fallDistance=3;
                DamageHud.attacked(target);DamageHud.attacked(other);sound(mc,target);clock.settle(mc);
                near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Ambiguous positional sound must not choose a target");
                var blockSound=target.getOffhandItem().get(DataComponents.BLOCKS_ATTACKS).blockSound().orElseThrow();
                mc.getConnection().handleSoundEntityEvent(new ClientboundSoundEntityPacket(blockSound,target.getSoundSource(),target,1,1,0));
                clock.settle(mc);check(ShieldDamageHud.TRACKER.total(target.getUUID())>0,"Entity-bound sound identifies target directly");
                near(0,ShieldDamageHud.TRACKER.total(other.getUUID()),"Other target must remain untouched");

                configure(mc,true,true);fresh(target);p.fallDistance=3;DamageHud.attacked(target);
                mc.getConnection().handleDamageEvent(new ClientboundDamageEventPacket(target,p.getMainHandItem().getDamageSource(other)));
                sound(mc,target);equip(mc,target,10);clock.settle(mc);
                near(0,ShieldDamageHud.TRACKER.total(target.getUUID()),"Another attacker invalidates local attribution");
                configure(mc,true,true);fresh(target);DamageHud.attacked(target);sound(mc,target);DamageHud.playerDied(target.getUUID());clock.settle(mc);
                check(DamageHud.visibleHit().isEmpty(),"Death cancels delayed result");
                configure(mc,true,true);fresh(target);DamageHud.attacked(target);sound(mc,target);p.setHealth(0);clock.tick(mc);p.setHealth(20);clock.settle(mc);
                check(DamageHud.visibleHit().isEmpty(),"Local death cancels delayed result");
                configure(mc,true,true);fresh(target);p.fallDistance=8;double saved=MaceDamageCalculator.atAttack(p,target);
                DamageHud.attacked(target);health(mc,target);p.resetFallDistance();clock.tick(mc);p.fallDistance=4;clock.settle(mc);
                near(saved,DamageHud.visibleHitAmount(),"Later fall state cannot change a saved smash");
                near(4,p.fallDistance,"Delayed smash confirmation cannot reset a new fall");

                configure(mc,true,true);fresh(target);p.setYRot(180);p.setXRot(0);
                p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SPEAR));
                double jab=MaceDamageCalculator.effectiveAttackDamage(p);
                DamageHud.spearJab(p.getMainHandItem().get(DataComponents.PIERCING_WEAPON));
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH,200,0));
                clock.advance(300);health(mc,target);clock.settle(mc);
                near(jab,DamageHud.visibleHitAmount(),"Off-ray spear confirmation uses the saved attribute snapshot");
                p.removeEffect(net.minecraft.world.effect.MobEffects.STRENGTH);

                configure(mc,true,true);fresh(target);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SPEAR));
                double oldestJab=MaceDamageCalculator.effectiveAttackDamage(p);
                DamageHud.spearJab(p.getMainHandItem().get(DataComponents.PIERCING_WEAPON));
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH,200,0));
                DamageHud.spearJab(p.getMainHandItem().get(DataComponents.PIERCING_WEAPON));health(mc,target);clock.settle(mc);
                near(oldestJab,DamageHud.visibleHitAmount(),"Different off-ray spear predictions use the oldest saved estimate");
                p.removeEffect(net.minecraft.world.effect.MobEffects.STRENGTH);
                configure(mc,true,true);fresh(target);DamageHud.attacked(target);clock.advance(6001);sound(mc,target);health(mc,target);clock.settle(mc);
                check(DamageHud.visibleHit().isEmpty(),"Expired attacks cannot be revived");
                mc.level.removeEntity(target.getId(),net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                configure(mc,true,true);fresh(other);p.fallDistance=3;DamageHud.attacked(other);sound(mc,other);
                check(!DamageHud.HISTORY.entries().isEmpty(),"Disconnect fixture must leave a pending result");
              }
            });
        }finally{context.runOnClient(mc->{
            if(mc.level==null) {
                DamageHud.tick(mc);check(DamageHud.HISTORY.entries().isEmpty(),"Disconnect clears pending attacks");
                check(DamageHud.visibleHit().isEmpty()&&!ShieldDamageHud.hitVisible(),"Disconnect clears combat displays");
            }
            CombatTestClock.reset(mc);if(original.get()!=null)SylvyesPvPHud.DAMAGE_CONFIG.activate(original.get());
        });}
    }
}
