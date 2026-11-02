package dev.sylvyespvphud;

import java.util.ArrayList;
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
    static final AttackHistory<CombatAttempt> HISTORY = new AttackHistory<>();
    private record HealthResult(CombatAttempt attack,double estimate,long received) {}
    private static final List<HealthResult> healthResults=new ArrayList<>();
    private static final Map<java.util.UUID,Double> reportedHealth=new LinkedHashMap<>();
    private static int displayTicks;
    private static double hitAmount = Double.NaN;
    private static String hit = "";
    private static boolean hitCritical;
    private static Object level;
    private static final Map<Integer,Long> spearContacts=new LinkedHashMap<>();
    private static final List<SpearSnapshot> spearFallbacks=new ArrayList<>();
    private static long fallbackUncertainUntil=Long.MIN_VALUE;
    static long fallGeneration;
    private static double previousFall=Double.NaN;
    private static boolean previouslyGrounded,previouslyFlying;
    private static void observeFall(Minecraft mc) {
        if(mc.player==null)return;
        double fall=mc.player.fallDistance;
        if(Double.isFinite(previousFall)&&(fall<previousFall||mc.player.onGround()&&!previouslyGrounded
                ||mc.player.isFallFlying()&&!previouslyFlying))fallGeneration++;
        previousFall=fall;previouslyGrounded=mc.player.onGround();previouslyFlying=mc.player.isFallFlying();
    }
    private static final DamageDeathMarkers deadPlayers=new DamageDeathMarkers();
    private static boolean localPlayerDead;
    private DamageHud() {}
    static void remember(CombatAttempt candidate) {
        if(candidate.entry!=null)return;
        long now=CombatTiming.now(),remaining=candidate.created+candidate.retention-now;
        if(remaining<=0)return;
        for(var entry:HISTORY.entries())if(entry.value.target==candidate.target&&entry.value.token==candidate.token) {
            entry.value.contact|=candidate.contact;return;
        }
        if(candidate.target instanceof Player player&&player.isAlive()&&!player.isDeadOrDying())deadPlayers.revive(player.getUUID());
        candidate.entry=HISTORY.add(candidate.target.getUUID(),candidate,now,remaining,candidate.settling);
    }
    static void clearPending() {
        HISTORY.clear();healthResults.clear();reportedHealth.clear();spearFallbacks.clear();fallbackUncertainUntil=Long.MIN_VALUE;latestDisplayAttack=Long.MIN_VALUE;previousFall=Double.NaN;fallGeneration++;
    }
    public static void attacked(Entity entity) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||!(entity instanceof LivingEntity target))return;
        var config=SylvyesPvPHud.DAMAGE_CONFIG.current();var weapon=mc.player.getMainHandItem().copy();
        if(!config.enabled()||(!config.hitEnabled()&&!config.trackerEnabled()&&!config.shieldTrackerEnabled())||!DamageWeapon.of(weapon).enabled(config))return;
        observeFall(mc);
        double raw=MaceDamageCalculator.atAttack(mc.player,target);var shields=ShieldDamageCalculator.potential(target,weapon.getDamageSource(mc.player));
        boolean critical=DamageWeapon.of(weapon)!=DamageWeapon.SPEAR
                && MaceDamageCalculator.criticalAtAttack(mc.player,target,AttributeSwaps.attackCooldown(mc));
        boolean smash=DamageWeapon.of(weapon)==DamageWeapon.MACE&&mc.player.fallDistance>1.5&&!mc.player.isFallFlying();
        remember(new CombatAttempt(target,weapon,raw,mc.player.fallDistance,critical,smash,shields,new Object(),true));
    }
    private record SpearSnapshot(Map<java.util.UUID,CombatAttempt> attacks,long expires) {}
    private static SpearSnapshot captureSpear(Minecraft mc,ItemStack weapon,java.util.function.ToDoubleFunction<LivingEntity> raw) {
        var attacks=new LinkedHashMap<java.util.UUID,CombatAttempt>();Object token=new Object();
        double range=mc.player.getAttackRangeWith(weapon).effectiveMaxRange(mc.player)+1;
        for(var target:mc.level.getEntitiesOfClass(LivingEntity.class,mc.player.getBoundingBox().inflate(range),e->e!=mc.player)) {
            if(attacks.size()>=32)break;
            attacks.put(target.getUUID(),new CombatAttempt(target,weapon,raw.applyAsDouble(target),mc.player.fallDistance,false,false,
                    ShieldDamageCalculator.potential(target,weapon.getDamageSource(mc.player)),token,false));
        }
        long now=CombatTiming.now();spearFallbacks.removeIf(snapshot->snapshot.expires<=now);
        var snapshot=new SpearSnapshot(Map.copyOf(attacks),now+CombatTiming.retention(CombatTiming.ping()));spearFallbacks.add(snapshot);
        if(spearFallbacks.size()>32)fallbackUncertainUntil=Math.max(fallbackUncertainUntil,spearFallbacks.removeFirst().expires);
        return snapshot;
    }
    private static void spearContact(LivingEntity target,SpearSnapshot snapshot) {
        var attack=snapshot.attacks.get(target.getUUID());if(attack==null)return;
        attack.contact=true;remember(attack);
    }
    public static void spearJab(PiercingWeapon piercing) {
        var mc=Minecraft.getInstance();var config=SylvyesPvPHud.DAMAGE_CONFIG.current();
        if(mc.player==null||mc.level==null||!config.enabled()||(!config.hitEnabled()&&!config.trackerEnabled()&&!config.shieldTrackerEnabled())||!config.spearEnabled())return;
        ItemStack weapon=mc.player.getMainHandItem().copy();if(DamageWeapon.of(weapon)!=DamageWeapon.SPEAR)return;
        double base=MaceDamageCalculator.effectiveAttackDamage(mc.player);
        var snapshot=captureSpear(mc,weapon,target->base+MaceDamageCalculator.enchantmentBonus(weapon,target));
        var hits=ProjectileUtil.getHitEntitiesAlong(mc.player,mc.player.getAttackRangeWith(weapon),e->PiercingWeapon.canHitEntity(mc.player,e),ClipContext.Block.COLLIDER)
                .map(block->List.<net.minecraft.world.phys.EntityHitResult>of(),entities->entities);
        for(var hit:hits){Entity entity=hit.getEntity();if(entity instanceof EnderDragonPart part)entity=part.parentMob;
            if(entity instanceof LivingEntity target)spearContact(target,snapshot);}
    }
    public static void damageEvent(ClientboundDamageEventPacket packet) {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        if(!(mc.level.getEntity(packet.entityId()) instanceof LivingEntity target))return;
        if(packet.sourceCauseId()!=mc.player.getId()) {
            HISTORY.discard(target.getUUID());healthResults.removeIf(r->r.attack.target==target);
            for(var snapshot:spearFallbacks) {
                var attack=snapshot.attacks.get(target.getUUID());if(attack!=null)attack.contaminated=true;
            }
            ShieldDamageHud.otherAttacker(packet.entityId());return;
        }
        long now=CombatTiming.now();
        if(DamageWeapon.of(mc.player.getMainHandItem())==DamageWeapon.SPEAR&&now>=fallbackUncertainUntil&&HISTORY.entries().stream().noneMatch(e->e.value.target==target&&!e.cancelled&&now<e.expires)) {
            for(var snapshot:spearFallbacks)if(snapshot.expires>now) {
                var attack=snapshot.attacks.get(target.getUUID());
                if(attack!=null&&!attack.contaminated&&attack.entry==null){attack.contact=true;remember(attack);}
            }
        }
        HISTORY.confirm(target.getUUID(),AttackHistory.Kind.HEALTH,a->a.contact,now);
    }
    private static void evaluateSpearCharge(Minecraft mc) {
        long time=CombatTiming.now();spearFallbacks.removeIf(snapshot->snapshot.expires<=time);
        var config=SylvyesPvPHud.DAMAGE_CONFIG.current();
        if(mc.player==null||!config.enabled()||(!config.hitEnabled()&&!config.trackerEnabled()&&!config.shieldTrackerEnabled())||!config.spearEnabled()||!mc.player.isUsingItem())return;
        ItemStack weapon=mc.player.getUseItem().copy();KineticWeapon kinetic=weapon.get(DataComponents.KINETIC_WEAPON);
        if(kinetic==null)return;
        int duration=weapon.getUseDuration(mc.player)-mc.player.getUseItemRemainingTicks()-kinetic.delayTicks();
        if(duration<0)return;
        Vec3 look=mc.player.getLookAngle();double forward=look.dot(KineticWeapon.getMotion(mc.player));
        double base=mc.player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        var relative=new LinkedHashMap<java.util.UUID,Double>();
        var snapshot=captureSpear(mc,weapon,target->{
            double speed=Math.max(0,forward-look.dot(KineticWeapon.getMotion(target)));relative.put(target.getUUID(),speed);
            return SpearDamageMath.raw(base,speed,kinetic.damageMultiplier())+MaceDamageCalculator.enchantmentBonus(weapon,target);
        });
        long now=mc.level.getGameTime();
        spearContacts.entrySet().removeIf(entry->now-entry.getValue()>=kinetic.contactCooldownTicks());
        var hits=ProjectileUtil.getHitEntitiesAlong(mc.player,mc.player.getAttackRangeWith(weapon),e->PiercingWeapon.canHitEntity(mc.player,e),ClipContext.Block.COLLIDER)
                .map(block->List.<net.minecraft.world.phys.EntityHitResult>of(),entities->entities);
        for(var hit:hits) {
            Entity entity=hit.getEntity();if(entity instanceof EnderDragonPart part)entity=part.parentMob;
            if(!(entity instanceof LivingEntity target)||!relative.containsKey(target.getUUID()))continue;
            if(kinetic.damageConditions().isEmpty()||!kinetic.damageConditions().get().test(duration,forward,relative.get(target.getUUID()),1))continue;
            Long last=spearContacts.get(target.getId());if(last!=null&&now-last<kinetic.contactCooldownTicks())continue;
            spearContacts.put(target.getId(),now);spearContact(target,snapshot);
        }
    }
    public static void entityMoved(int entityId) {
        long now=CombatTiming.now();
        for(var entry:HISTORY.entries())if(entry.value.target.getId()==entityId)entry.value.samplePosition(now);
    }
    public static void entityEvent(Entity entity,byte event) {
        if(entity instanceof LivingEntity living) {
            if(event==47)ShieldDamageHud.broken(living,EquipmentSlot.MAINHAND);
            if(event==48)ShieldDamageHud.broken(living,EquipmentSlot.OFFHAND);
        }
        if(event==35)for(var entry:HISTORY.entries())if(entry.value.target==entity)entry.value.totem=true;
        if(event==3&&entity instanceof Player player)playerDied(player.getUUID());
    }
    static void playerDied(java.util.UUID id) {
        HISTORY.discard(id);healthResults.removeIf(r->r.attack.target.getUUID().equals(id));reportedHealth.remove(id);
        ShieldDamageHud.died(id);deadPlayers.mark(id);CumulativeDamageTracker.fade(id);
    }
    public static void tick(Minecraft mc){
        if(mc.level!=level||mc.player==null){level=mc.level;ShieldDamageHud.reset();clearPending();spearContacts.clear();displayTicks=0;hit="";CumulativeDamageTracker.resetAll();deadPlayers.clear();localPlayerDead=false;return;}
        if(!mc.player.isAlive()||mc.player.isDeadOrDying()){
            if(!localPlayerDead){ShieldDamageHud.reset();clearPending();spearContacts.clear();displayTicks=0;hit="";CumulativeDamageTracker.resetAll(true);deadPlayers.clear();localPlayerDead=true;}
            CumulativeDamageTracker.tick();return;
        }
        localPlayerDead=false;observeFall(mc);
        ShieldDamageHud.tick();
        if(displayTicks>0)displayTicks--;
        CumulativeDamageTracker.tick();
        for(Player player:mc.level.players())if(!player.isAlive()||player.isDeadOrDying())playerDied(player.getUUID());
        var config=SylvyesPvPHud.DAMAGE_CONFIG.current();
        if(!config.trackerEnabled()){CumulativeDamageTracker.resetAll();deadPlayers.clear();}
        if(!config.enabled()||(!config.hitEnabled()&&!config.trackerEnabled()&&!config.shieldTrackerEnabled())){clearPending();displayTicks=0;if(!config.enabled()||!config.trackerEnabled()){CumulativeDamageTracker.resetAll();deadPlayers.clear();}return;}
        long now=CombatTiming.now();
        for(var entry:HISTORY.entries()) {
            if(!entry.value.type.enabled(config)||entry.value.target.isRemoved())HISTORY.discard(entry.target);
            else entry.value.samplePosition(now);
        }
        for(var result:HISTORY.resolve(now,CombatAttempt::healthSignature,CombatAttempt::blockSignature).stream()
                .sorted(java.util.Comparator.comparingLong(r->r.entry().value.sequence)).toList()) {
            if(result.evidence().kind==AttackHistory.Kind.BLOCK)ShieldDamageHud.resolved(result);
            else queueHealth(result.entry().value,result.entry().value.unblocked,result.evidence().received);
        }
        finalizeHealth(now);
        ShieldDamageHud.finalizeResults(now);
        for(var id:deadPlayers.ids())if(HISTORY.entries().stream().noneMatch(e->e.target.equals(id)))CumulativeDamageTracker.reset(id);
        evaluateSpearCharge(mc);
    }
    static void clearHealthDisplay(){displayTicks=0;hit="";}
    static void queueHealth(CombatAttempt attack,double estimate,long received) {
        if(attack.healthApplied)return;
        attack.healthApplied=true;
        if(estimate>0)healthResults.add(new HealthResult(attack,estimate,received));
    }
    private static void finalizeHealth(long now) {
        var ready=healthResults.stream().filter(r->r.attack.calculated||r.attack.before>r.attack.target.getHealth()
                ||r.attack.totem||now-r.received>=500).toList();
        for(var result:ready)if(result.attack.calculated)show(result.attack,result.estimate);
        for(var id:ready.stream().filter(r->!r.attack.calculated).map(r->r.attack.target.getUUID()).distinct().toList()) {
            var batch=ready.stream().filter(r->!r.attack.calculated&&r.attack.target.getUUID().equals(id)).toList();
            double before=batch.stream().mapToDouble(r->r.attack.before).max().orElse(0);
            before=Math.min(before,reportedHealth.getOrDefault(id,before));
            double health=batch.getFirst().attack.target.getHealth(),observed=Math.max(0,before-health);
            double weight=batch.stream().mapToDouble(r->r.estimate).sum();
            for(var r:batch)show(r.attack,observed>0?observed*r.estimate/weight:r.estimate);
            if(observed>0)reportedHealth.put(id,health);
        }
        healthResults.removeAll(ready);
        reportedHealth.replaceAll((id,previous)->{
            var mc=Minecraft.getInstance();var target=mc.level.getPlayerByUUID(id);
            return target==null?previous:Math.max(previous,target.getHealth());
        });
    }
    private static long latestDisplayAttack=Long.MIN_VALUE;
    static boolean allowDisplay(long sequence){if(sequence<latestDisplayAttack)return false;latestDisplayAttack=sequence;return true;}
    private static void show(CombatAttempt attack,double amount) {
        if(!Double.isFinite(amount)||amount<=0)return;var config=SylvyesPvPHud.DAMAGE_CONFIG.current();
        if(!config.enabled()||!attack.type.enabled(config))return;
        if(config.trackerEnabled()&&attack.target instanceof Player player)recordTrackerHit(player.getUUID(),player.getName().getString(),amount,config.trackerSeconds()*20,config.trackerHudSeconds()*20,config.trackerHudAlwaysVisible(),!player.isAlive()||player.isDeadOrDying());
        if(attack.smash&&attack.fallGeneration==fallGeneration&&HISTORY.entries().stream().noneMatch(e->e.value.sequence>attack.sequence)) {
            Minecraft.getInstance().player.resetFallDistance();observeFall(Minecraft.getInstance());
        }
        if(config.hitEnabled()&&allowDisplay(attack.sequence)) {
            ShieldDamageHud.healthHit(attack.target.getUUID());hit=DamageText.format(config.hitTemplate(),attack.blocks,amount);
            hitAmount=amount;hitCritical=attack.critical;displayTicks=config.hitSeconds()*20;
        }
    }
    static void recordTrackerHit(java.util.UUID id,String name,double amount,int storageTicks,int hudTicks,boolean always,boolean deadNow){
        CumulativeDamageTracker.record(id,name,amount,storageTicks,hudTicks,always);
        if(deadNow||deadPlayers.contains(id)){deadPlayers.mark(id);CumulativeDamageTracker.reset(id);}
    }
    static boolean deathMarked(java.util.UUID id){return deadPlayers.contains(id);}
    static void playerRevived(java.util.UUID id){deadPlayers.revive(id);}
    public static void resetTracker(){ShieldDamageHud.resetTracker();CumulativeDamageTracker.resetAll(true);deadPlayers.clear();}
    static String visibleHit(){return ShieldDamageHud.hitVisible()?ShieldDamageHud.hitText(SylvyesPvPHud.DAMAGE_CONFIG.current()):displayTicks>0?hit:"";}
    static double visibleHitAmount(){return ShieldDamageHud.hitVisible()?ShieldDamageHud.HITS.amount():displayTicks>0?hitAmount:Double.NaN;}
    static boolean visibleHitCritical(){return !ShieldDamageHud.hitVisible()&&displayTicks>0&&hitCritical;}
    static boolean showFall(double distance){return showFall(distance,1.5);}
    static boolean showFall(double distance,double threshold){return Double.isFinite(distance)&&Double.isFinite(threshold)&&distance>threshold;}
    public static void extract(GuiGraphicsExtractor g,DeltaTracker delta){var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.level==null||mc.gui.screen()!=null||mc.gui.hud.isHidden()||!p.isAlive()||p.isSpectator())return;var c=SylvyesPvPHud.DAMAGE_CONFIG.current();if(!c.enabled())return;double fallBlocks=p.fallDistance;if(c.fallEnabled()&&showFall(fallBlocks,c.fallThreshold()))HudRenderer.textColor(g,DamageText.format(c.fallTemplate(),fallBlocks,0),SylvyesPvPHud.HUD_CONFIG.current().fall(),c.fallColors().color(fallBlocks));if(c.hitEnabled()&&!visibleHit().isEmpty()){Component text=Component.literal(visibleHit());if(c.boldCriticalDamage()&&visibleHitCritical())text=text.copy().withStyle(ChatFormatting.BOLD);HudRenderer.textColor(g,text,SylvyesPvPHud.HUD_CONFIG.current().hit(),c.effectiveHitColors().color(visibleHitAmount()));}String total=CumulativeDamageTracker.visible(c.trackerTemplate());if(c.trackerEnabled()&&!total.isEmpty())HudRenderer.text(g,total,SylvyesPvPHud.HUD_CONFIG.current().damageTracker(),0,CumulativeDamageTracker.opacity());String shieldTotal=ShieldDamageHud.TRACKER.visible(c.shieldTrackerTemplate());if(c.shieldTrackerEnabled()&&!shieldTotal.isEmpty())HudRenderer.text(g,shieldTotal,SylvyesPvPHud.HUD_CONFIG.current().shieldTracker(),0,ShieldDamageHud.TRACKER.opacity());}
}
