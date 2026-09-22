package dev.sylvyespvphud;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class DamageHud {
    private static final Map<Integer, Pending> pending = new LinkedHashMap<>();
    private static int displayTicks;
    private static double hitAmount = Double.NaN;
    private static String hit = "";
    private static boolean hitCritical;
    private static Object level;
    private static final Map<Integer,Long> spearContacts=new LinkedHashMap<>();
    private static ChargeSnapshot recentCharge;
    private static JabSnapshot recentJab;
    private static final DamageDeathMarkers deadPlayers=new DamageDeathMarkers();
    private DamageHud() {}
    private static final class Pending {
        final LivingEntity target; final float before; final double blocks, amount; final boolean calculated, critical, resetsFallDistance;
        int ticks=40, confirmedTicks; boolean confirmed, totem;
        Pending(LivingEntity target,double blocks,double amount,boolean calculated,boolean critical,boolean resetsFallDistance){this.target=target;before=target.getHealth();this.blocks=blocks;this.amount=amount;this.calculated=calculated;this.critical=critical;this.resetsFallDistance=resetsFallDistance;}
    }
    private static void remember(Pending candidate) {
        if(candidate.target instanceof Player player&&player.isAlive()&&!player.isDeadOrDying())deadPlayers.revive(player.getUUID());
        pending.compute(candidate.target.getId(),(id,current)->current!=null&&current.confirmed?current:candidate);
    }
    public static void attacked(Entity entity) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||!(entity instanceof LivingEntity target))return;
        var config=SylvyesPvPHud.DAMAGE_CONFIG.current();var weapon=mc.player.getMainHandItem().copy();
        if(!config.enabled()||(!config.hitEnabled()&&!config.trackerEnabled())||!DamageWeapon.of(weapon).enabled(config))return;
        double amount=MaceDamageCalculator.atAttack(mc.player,target);
        if(config.calculatedDamage()&&config.useEnemyGear())amount=MaceDamageCalculator.afterGear(amount,weapon,target);
        boolean critical=DamageWeapon.of(weapon)!=DamageWeapon.SPEAR
                && MaceDamageCalculator.criticalAtAttack(mc.player,target,AttributeSwaps.attackCooldown(mc));
        boolean smash=DamageWeapon.of(weapon)==DamageWeapon.MACE&&mc.player.fallDistance>1.5&&!mc.player.isFallFlying();
        remember(new Pending(target,mc.player.fallDistance,amount,config.calculatedDamage(),critical,smash));
    }
    private record ChargeSnapshot(ItemStack weapon,int duration,Vec3 look,double attackerForward,int age) {}
    private record JabSnapshot(ItemStack weapon,double baseDamage,int age) {}
    private static void registerSpearJab(LivingEntity target,JabSnapshot snapshot) {
        var mc=Minecraft.getInstance();var config=SylvyesPvPHud.DAMAGE_CONFIG.current();if(mc.player==null)return;
        double amount=snapshot.baseDamage()+MaceDamageCalculator.enchantmentBonus(snapshot.weapon(),target);
        if(config.calculatedDamage()&&config.useEnemyGear())amount=MaceDamageCalculator.afterGear(amount,snapshot.weapon(),target);
        remember(new Pending(target,mc.player.fallDistance,amount,config.calculatedDamage(),false,false));
    }
    public static void spearJab(PiercingWeapon piercing) {
        var mc=Minecraft.getInstance();var config=SylvyesPvPHud.DAMAGE_CONFIG.current();
        if(mc.player==null||mc.level==null||!config.enabled()||(!config.hitEnabled()&&!config.trackerEnabled())||!config.spearEnabled())return;
        ItemStack weapon=mc.player.getMainHandItem().copy();if(DamageWeapon.of(weapon)!=DamageWeapon.SPEAR)return;
        recentJab=new JabSnapshot(weapon,MaceDamageCalculator.effectiveAttackDamage(mc.player),0);
        var range=mc.player.getAttackRangeWith(weapon);
        var hits=ProjectileUtil.getHitEntitiesAlong(mc.player,range,e->PiercingWeapon.canHitEntity(mc.player,e),ClipContext.Block.COLLIDER)
                .map(block->List.<net.minecraft.world.phys.EntityHitResult>of(),entities->entities);
        for(var hit:hits){Entity entity=hit.getEntity();if(entity instanceof EnderDragonPart part)entity=part.parentMob;
            if(entity instanceof LivingEntity target)registerSpearJab(target,recentJab);}
    }
    private static void registerSpearCharge(LivingEntity target,ChargeSnapshot snapshot,KineticWeapon kinetic,boolean requireConditions) {
        var mc=Minecraft.getInstance();var config=SylvyesPvPHud.DAMAGE_CONFIG.current();
        if(mc.player==null)return;
        double targetForward=snapshot.look().dot(KineticWeapon.getMotion(target));
        double relative=Math.max(0,snapshot.attackerForward()-targetForward);
        if(requireConditions&&(kinetic.damageConditions().isEmpty()||!kinetic.damageConditions().get().test(snapshot.duration(),snapshot.attackerForward(),relative,1)))return;
        double amount=SpearDamageMath.raw(mc.player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE),relative,kinetic.damageMultiplier());
        amount+=MaceDamageCalculator.enchantmentBonus(snapshot.weapon(),target);
        if(config.calculatedDamage()&&config.useEnemyGear())amount=MaceDamageCalculator.afterGear(amount,snapshot.weapon(),target);
        remember(new Pending(target,mc.player.fallDistance,amount,config.calculatedDamage(),false,false));
    }
    public static void damageEvent(ClientboundDamageEventPacket packet){var mc=Minecraft.getInstance();if(mc.player==null||packet.sourceCauseId()!=mc.player.getId())return;Pending p=pending.get(packet.entityId());
        if(p==null&&mc.level!=null&&mc.level.getEntity(packet.entityId()) instanceof LivingEntity target){
            if(recentJab!=null&&recentJab.age()<40&&(recentCharge==null||recentJab.age()<=recentCharge.age()))registerSpearJab(target,recentJab);
            else if(recentCharge!=null&&recentCharge.age()<40){KineticWeapon kinetic=recentCharge.weapon().get(DataComponents.KINETIC_WEAPON);if(kinetic!=null)registerSpearCharge(target,recentCharge,kinetic,false);}
            p=pending.get(packet.entityId());}
        if(p!=null){p.confirmed=true;if(p.resetsFallDistance)mc.player.resetFallDistance();}}
    private static void evaluateSpearCharge(Minecraft mc) {
        var config=SylvyesPvPHud.DAMAGE_CONFIG.current();
        if(recentJab!=null)recentJab=new JabSnapshot(recentJab.weapon(),recentJab.baseDamage(),recentJab.age()+1);
        if(recentCharge!=null)recentCharge=new ChargeSnapshot(recentCharge.weapon(),recentCharge.duration(),recentCharge.look(),recentCharge.attackerForward(),recentCharge.age()+1);
        if(mc.player==null||!config.enabled()||(!config.hitEnabled()&&!config.trackerEnabled())||!config.spearEnabled()||!mc.player.isUsingItem())return;
        ItemStack weapon=mc.player.getUseItem();KineticWeapon kinetic=weapon.get(DataComponents.KINETIC_WEAPON);
        if(kinetic==null)return;
        int duration=weapon.getUseDuration(mc.player)-mc.player.getUseItemRemainingTicks();
        if(duration<kinetic.delayTicks())return;
        duration-=kinetic.delayTicks();Vec3 look=mc.player.getLookAngle();double attackerForward=look.dot(KineticWeapon.getMotion(mc.player));
        recentCharge=new ChargeSnapshot(weapon.copy(),duration,look,attackerForward,0);
        var range=mc.player.getAttackRangeWith(weapon);long now=mc.level.getGameTime();
        double nearbyRange=range.effectiveMaxRange(mc.player)+1;
        for(var target:mc.level.getEntitiesOfClass(LivingEntity.class,mc.player.getBoundingBox().inflate(nearbyRange),entity->entity!=mc.player))
            registerSpearCharge(target,recentCharge,kinetic,true);
        spearContacts.entrySet().removeIf(entry->now-entry.getValue()>=kinetic.contactCooldownTicks());
        var hits=ProjectileUtil.getHitEntitiesAlong(mc.player,range,e->PiercingWeapon.canHitEntity(mc.player,e),ClipContext.Block.COLLIDER)
                .map(block->List.<net.minecraft.world.phys.EntityHitResult>of(),entities->entities);
        for(var hit:hits){Entity entity=hit.getEntity();if(entity instanceof EnderDragonPart part)entity=part.parentMob;if(!(entity instanceof LivingEntity target))continue;
            Long last=spearContacts.get(target.getId());if(last!=null&&now-last<kinetic.contactCooldownTicks())continue;
            spearContacts.put(target.getId(),now);registerSpearCharge(target,recentCharge,kinetic,true);}
    }
    public static void entityEvent(Entity entity,byte event){if(event==35){Pending p=pending.get(entity.getId());if(p!=null)p.totem=true;}if(event==3&&entity instanceof Player player)playerDied(player.getUUID());}
    static void playerDied(java.util.UUID id){deadPlayers.mark(id);CumulativeDamageTracker.fade(id);}
    public static void tick(Minecraft mc){
        if(mc.level!=level||mc.player==null||!mc.player.isAlive()){level=mc.level;pending.clear();spearContacts.clear();recentCharge=null;recentJab=null;displayTicks=0;hit="";CumulativeDamageTracker.resetAll();deadPlayers.clear();return;}
        if(displayTicks>0)displayTicks--;
        CumulativeDamageTracker.tick();
        for(Player player:mc.level.players())if(!player.isAlive()||player.isDeadOrDying())playerDied(player.getUUID());
        var config=SylvyesPvPHud.DAMAGE_CONFIG.current();
        if(!config.trackerEnabled()){CumulativeDamageTracker.resetAll();deadPlayers.clear();}
        if(!config.enabled()||(!config.hitEnabled()&&!config.trackerEnabled())){pending.clear();displayTicks=0;if(!config.enabled()||!config.trackerEnabled()){CumulativeDamageTracker.resetAll();deadPlayers.clear();}return;}
        Iterator<Pending> iterator=pending.values().iterator();
        while(iterator.hasNext()){Pending p=iterator.next();p.ticks--;if(p.confirmed)p.confirmedTicks++;float observed=p.before-p.target.getHealth();
            if(p.confirmed&&p.calculated){show(p,p.amount);iterator.remove();}
            else if(p.confirmed&&observed>0){show(p,observed);iterator.remove();}
            else if(p.confirmed&&(p.totem||p.confirmedTicks>=10)){show(p,p.amount);iterator.remove();}
            else if(p.ticks<=0)iterator.remove();}
        for(var id:deadPlayers.ids())if(pending.values().stream().noneMatch(p->p.target instanceof Player player&&player.getUUID().equals(id)))CumulativeDamageTracker.reset(id);
        evaluateSpearCharge(mc);
    }
    private static void show(Pending pendingHit,double amount){var config=SylvyesPvPHud.DAMAGE_CONFIG.current();
        if(config.trackerEnabled()&&pendingHit.target instanceof Player player)recordTrackerHit(player.getUUID(),player.getName().getString(),amount,config.trackerSeconds()*20,config.trackerHudSeconds()*20,config.trackerHudAlwaysVisible(),!player.isAlive()||player.isDeadOrDying());
        if(config.hitEnabled()){hit=DamageText.format(config.hitTemplate(),pendingHit.blocks,amount);hitAmount=amount;hitCritical=pendingHit.critical;displayTicks=config.hitSeconds()*20;}}
    static void recordTrackerHit(java.util.UUID id,String name,double amount,int storageTicks,int hudTicks,boolean always,boolean deadNow){
        CumulativeDamageTracker.record(id,name,amount,storageTicks,hudTicks,always);
        if(deadNow||deadPlayers.contains(id)){deadPlayers.mark(id);CumulativeDamageTracker.reset(id);}
    }
    static boolean deathMarked(java.util.UUID id){return deadPlayers.contains(id);}
    static void playerRevived(java.util.UUID id){deadPlayers.revive(id);}
    public static void resetTracker(){CumulativeDamageTracker.resetAll(true);deadPlayers.clear();}
    static String visibleHit(){return displayTicks>0?hit:"";}
    static double visibleHitAmount(){return displayTicks>0?hitAmount:Double.NaN;}
    static boolean visibleHitCritical(){return displayTicks>0&&hitCritical;}
    static boolean showFall(double distance){return showFall(distance,1.5);}
    static boolean showFall(double distance,double threshold){return Double.isFinite(distance)&&Double.isFinite(threshold)&&distance>threshold;}
    public static void extract(GuiGraphicsExtractor g,DeltaTracker delta){var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.level==null||mc.gui.screen()!=null||mc.gui.hud.isHidden()||!p.isAlive()||p.isSpectator())return;var c=SylvyesPvPHud.DAMAGE_CONFIG.current();if(!c.enabled())return;double fallBlocks=p.fallDistance;if(c.fallEnabled()&&showFall(fallBlocks,c.fallThreshold()))HudRenderer.textColor(g,DamageText.format(c.fallTemplate(),fallBlocks,0),SylvyesPvPHud.HUD_CONFIG.current().fall(),c.fallColors().color(fallBlocks));if(c.hitEnabled()&&displayTicks>0){Component text=Component.literal(hit);if(c.boldCriticalDamage()&&hitCritical)text=text.copy().withStyle(ChatFormatting.BOLD);HudRenderer.textColor(g,text,SylvyesPvPHud.HUD_CONFIG.current().hit(),c.effectiveHitColors().color(hitAmount));}String total=CumulativeDamageTracker.visible(c.trackerTemplate());if(c.trackerEnabled()&&!total.isEmpty())HudRenderer.text(g,total,SylvyesPvPHud.HUD_CONFIG.current().damageTracker(),0,CumulativeDamageTracker.opacity());}
}
