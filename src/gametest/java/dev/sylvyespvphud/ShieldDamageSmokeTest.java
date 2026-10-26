package dev.sylvyespvphud;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.KineticWeapon;

public final class ShieldDamageSmokeTest implements FabricClientGameTest {
    private static final class ShieldTarget extends RemotePlayer {
        ShieldTarget(net.minecraft.client.multiplayer.ClientLevel level){super(level,new GameProfile(UUID.randomUUID(),"ShieldTarget"));}
        // Remote players start/stop use through synced server flags, not local input.
        @Override public void startUsingItem(InteractionHand hand){setLivingEntityFlag(2,hand==InteractionHand.OFF_HAND);setLivingEntityFlag(1,true);}
        @Override public void stopUsingItem(){super.stopUsingItem();setLivingEntityFlag(1,false);}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double expected,double actual,String message){check(Math.abs(expected-actual)<.001,message+": "+actual);}
    private static void configure(boolean calculated,boolean gear){
        var d=DamageConfig.defaults();
        SylvyesPvPHud.DAMAGE_CONFIG.activate(new DamageConfig(2,true,d.fallColor(),1,0,14,true,d.hitColor(),1,0,28,1,
                calculated,"{blocks} blocks","{damage} damage",1.5,d.fallColors(),d.hitColors(),true,true,true,gear,true,
                true,true,1,d.trackerTemplate(),1,false));
    }
    private static ItemStack shield(float delay,float factor,Optional<HolderSet<net.minecraft.world.damagesource.DamageType>> bypass){
        var item=new ItemStack(Items.SHIELD);
        var original=item.get(DataComponents.BLOCKS_ATTACKS);
        item.set(DataComponents.BLOCKS_ATTACKS,new BlocksAttacks(delay,original.disableCooldownScale(),
                List.of(new BlocksAttacks.DamageReduction(90,Optional.empty(),0,factor)),original.itemDamage(),
                bypass,original.blockSound(),original.disableSound()));
        return item;
    }
    private static void raise(RemotePlayer target,ItemStack shield){
        target.stopUsingItem();target.setItemSlot(EquipmentSlot.OFFHAND,shield);target.startUsingItem(InteractionHand.OFF_HAND);
    }
    private static void confirm(Minecraft mc,RemotePlayer target){
        DamageHud.damageEvent(new ClientboundDamageEventPacket(target,mc.player.getMainHandItem().getDamageSource(mc.player)));
    }
    @Override public void runTest(ClientGameTestContext context){
        var original=new java.util.concurrent.atomic.AtomicReference<DamageConfig>();
        try(var world=context.worldBuilder().create()){
            world.getConnection().waitForChunksRender();
            context.runOnClient(mc->{
                original.set(SylvyesPvPHud.DAMAGE_CONFIG.current());
                var p=mc.player;DamageHud.tick(mc);AttributeSwaps.endTick();
                var target=new ShieldTarget(mc.level);
                target.setId(2000010);target.setHealth(20);target.setPos(p.getX(),p.getY(),p.getZ()+2);
                target.setYHeadRot(180);mc.level.addEntity(target);
                p.setXRot(0);p.setYRot(0);p.setSprinting(true);
                p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.MACE));
                var source=p.getMainHandItem().getDamageSource(p);
                var normal=new ItemStack(Items.SHIELD);
                target.setItemSlot(EquipmentSlot.OFFHAND,normal);
                near(10,ShieldDamageCalculator.capture(target,source).remaining(10),"Idle shield");
                raise(target,normal);
                near(10,ShieldDamageCalculator.capture(target,source).remaining(10),"Shield activation delay");
                for(int i=0;i<6;i++)target.tick();
                target.setYHeadRot(180);
                near(0,ShieldDamageCalculator.capture(target,source).remaining(10),"Active vanilla shield");
                raise(target,shield(0,1,Optional.empty()));
                int durability=target.getOffhandItem().getDamageValue();
                var snapshot=ShieldDamageCalculator.capture(target,source);
                near(0,snapshot.remaining(10),"Frontal block");
                check(target.getOffhandItem().getDamageValue()==durability&&target.isUsingItem(),"Prediction must not damage or disable shields");
                target.setYHeadRot(0);
                near(10,ShieldDamageCalculator.capture(target,source).remaining(10),"Attack from behind");
                near(0,snapshot.remaining(10),"Facing must be snapshotted");
                target.setYHeadRot(180);target.stopUsingItem();
                near(10,ShieldDamageCalculator.capture(target,source).remaining(10),"Lowered or disabled shield");
                raise(target,shield(0,.5f,Optional.empty()));
                near(5,ShieldDamageCalculator.capture(target,source).remaining(10),"Partial block");
                raise(target,shield(0,1,Optional.of(HolderSet.direct(source.typeHolder()))));
                near(10,ShieldDamageCalculator.capture(target,source).remaining(10),"Damage type bypass");

                for(boolean calculated:new boolean[]{true,false})for(boolean gear:new boolean[]{false,true}){
                    configure(calculated,gear);DamageHud.resetTracker();
                    target.stopUsingItem();p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.MACE));p.fallDistance=0;
                    DamageHud.attacked(target);confirm(mc,target);
                    for(int i=0;i<10;i++)DamageHud.tick(mc);
                    double total=CumulativeDamageTracker.total(target.getUUID());
                    check(total>0,"Seed successful damage");
                    UUID other=UUID.randomUUID();
                    CumulativeDamageTracker.record(other,"Other",7,20,20,false);
                    String hit=DamageHud.visibleHit(),tracker=CumulativeDamageTracker.visible("{damage}");
                    for(var weapon:List.of(Items.MACE,Items.DIAMOND_SWORD,Items.DIAMOND_AXE,Items.IRON_SPEAR)){
                        p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(weapon));p.fallDistance=3;
                        target.stopUsingItem();DamageHud.attacked(target); // Older unconfirmed estimate.
                        raise(target,shield(0,1,Optional.empty()));DamageHud.attacked(target);
                        target.stopUsingItem();confirm(mc,target);DamageHud.tick(mc);
                        near(3,p.fallDistance,"Blocked smash must not reset fall distance");
                        near(total,CumulativeDamageTracker.total(target.getUUID()),"Blocked hit changed total");
                        check(other.equals(CumulativeDamageTracker.active()),"Blocked hit changed active tracker target");
                        check(hit.equals(DamageHud.visibleHit()),"Blocked hit replaced counter");
                        check(tracker.equals(CumulativeDamageTracker.visible("{damage}")),"Blocked hit replaced tracker");
                    }
                    // No blocked attack may refresh either display or storage expiry.
                    for(int i=0;i<20;i++)DamageHud.tick(mc);
                    check(DamageHud.visibleHit().isEmpty(),"Blocked hit refreshed counter timer");
                    near(0,CumulativeDamageTracker.total(target.getUUID()),"Blocked hit refreshed storage timer");
                    check(CumulativeDamageTracker.opacity()<1,"Blocked hit refreshed tracker HUD timer");
                }

                configure(true,false);target.stopUsingItem();p.fallDistance=0;
                p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.MACE));
                raise(target,shield(0,1,Optional.empty()));DamageHud.attacked(target);confirm(mc,target);DamageHud.tick(mc);
                target.stopUsingItem();DamageHud.attacked(target);confirm(mc,target);DamageHud.tick(mc);
                check(CumulativeDamageTracker.total(target.getUUID())>0,"Successful hit after block missing");
                double total=CumulativeDamageTracker.total(target.getUUID());
                // Look away so the jab has no predicted contact; confirmation must use saved defense.
                p.setYRot(180);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SPEAR));
                raise(target,shield(0,1,Optional.empty()));
                DamageHud.spearJab(p.getMainHandItem().get(DataComponents.PIERCING_WEAPON));
                target.stopUsingItem();for(int i=0;i<3;i++)DamageHud.tick(mc);confirm(mc,target);DamageHud.tick(mc);
                near(total,CumulativeDamageTracker.total(target.getUUID()),"Spear jab fallback lost shield snapshot");

                var spear=p.getMainHandItem();var kinetic=spear.get(DataComponents.KINETIC_WEAPON);
                spear.set(DataComponents.KINETIC_WEAPON,new KineticWeapon(kinetic.contactCooldownTicks(),0,
                        kinetic.dismountConditions(),kinetic.knockbackConditions(),Optional.empty(),kinetic.forwardMovement(),
                        kinetic.damageMultiplier(),kinetic.sound(),kinetic.hitSound()));
                p.startUsingItem(InteractionHand.MAIN_HAND);raise(target,shield(0,1,Optional.empty()));DamageHud.tick(mc);
                p.stopUsingItem();target.stopUsingItem();confirm(mc,target);DamageHud.tick(mc);
                near(total,CumulativeDamageTracker.total(target.getUUID()),"Spear charge fallback lost shield snapshot");

                configure(true,true);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.MACE));
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(20);
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
                raise(target,shield(0,.5f,Optional.empty()));
                double raw=MaceDamageCalculator.atAttack(p,target);
                double expected=MaceDamageCalculator.afterGear(raw*.5,p.getMainHandItem(),target);
                DamageHud.attacked(target);confirm(mc,target);DamageHud.tick(mc);
                near(expected,DamageHud.visibleHitAmount(),"Partial shield reduction must precede armor");
                near(total+expected,CumulativeDamageTracker.total(target.getUUID()),"Tracker must use partial block damage");
                total+=expected;

                configure(false,false);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.MACE));
                raise(target,shield(0,1,Optional.empty()));DamageHud.attacked(target);target.setHealth(18);confirm(mc,target);DamageHud.tick(mc);
                near(total+2,CumulativeDamageTracker.total(target.getUUID()),"Reported positive health loss must remain authoritative");
                mc.level.removeEntity(target.getId(),net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                DamageHud.resetTracker();
            });
        }finally{context.runOnClient(mc->{if(original.get()!=null)SylvyesPvPHud.DAMAGE_CONFIG.activate(original.get());});}
    }
}
