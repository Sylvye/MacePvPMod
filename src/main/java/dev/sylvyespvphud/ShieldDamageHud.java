package dev.sylvyespvphud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Shield evidence references the same attack history used by health damage. */
final class ShieldDamageHud {
    static final ShieldDamageTotals TRACKER=new ShieldDamageTotals(CumulativeDamageTracker.FADE_TICKS);
    static final ShieldDamageTotals HITS=new ShieldDamageTotals(0);
    private record ShieldKey(UUID target,EquipmentSlot slot) {}
    private static final Map<ShieldKey,ItemStack> serverShields=new HashMap<>();
    private static final List<Report> reports=new ArrayList<>();
    private static boolean shieldHitVisible;
    private static double hitBlocks;

    private static final class Report {
        final AttackHistory.Evidence<CombatAttempt> evidence;
        final Predicate<ShieldDamageCalculator.Snapshot> defense;
        final LivingEntity target;
        EquipmentSlot slot;
        CombatAttempt attack;
        ShieldDamageCalculator.Snapshot shield;
        boolean soundSeen,equipmentSeen,broken,breakApplied,applied,hitAllowed=true;
        double measured=Double.NaN,recordedAmount;
        Object trackerSession,hitSession;
        Report(AttackHistory.Evidence<CombatAttempt> evidence,Predicate<ShieldDamageCalculator.Snapshot> defense,EquipmentSlot slot) {
            this.evidence=evidence;this.defense=defense;this.slot=slot;target=evidence.candidates.getFirst().value.target;
        }
        ShieldDamageCalculator.Snapshot shield(CombatAttempt a) {
            var matches=a.shields.stream().filter(defense).filter(s->slot==null||s.slot()==slot).toList();
            return matches.size()==1?matches.getFirst().confirmed():null;
        }
        boolean matches(EquipmentSlot slot,ItemStack before) {
            if(this.slot!=null&&this.slot!=slot)return false;
            if(attack!=null)return shield!=null&&shield.slot()==slot&&sameShield(shield.shield(),before);
            return evidence.candidates.stream().anyMatch(e->e.value.shields.stream().anyMatch(s->s.slot()==slot&&defense.test(s)&&sameShield(s.shield(),before)));
        }
        double weight() {
            CombatAttempt a=attack!=null?attack:evidence.candidates.getFirst().value;
            var s=shield(a);return Math.max(1,s==null?0:s.durability(a.raw,false));
        }
    }
    static boolean enabled(DamageConfig c){return c.enabled()&&(c.shieldTrackerEnabled()||c.hitEnabled()&&c.hitShieldEnabled());}
    private static Report create(UUID id,Predicate<ShieldDamageCalculator.Snapshot> defense,EquipmentSlot slot,long now) {
        var event=DamageHud.HISTORY.confirm(id,AttackHistory.Kind.BLOCK,a->a.contact&&a.shieldWeapon()
                &&a.shields.stream().anyMatch(s->(slot==null||s.slot()==slot)&&defense.test(s)),now);
        if(event==null)return null;
        var report=new Report(event,defense,slot);reports.add(report);
        if(reports.size()>AttackHistory.TOTAL_LIMIT)cancel(reports.removeFirst());
        return report;
    }
    private static void cancel(Report r) {
        r.evidence.cancelled=true;
    }
    static void blockSound(Holder<SoundEvent> sound,Vec3 position,Integer entityId) {
        long now=CombatTiming.now();DamageHud.HISTORY.expire(now);
        Predicate<ShieldDamageCalculator.Snapshot> defense=s->s.blocking().blockSound()
                .map(block->block.value().location().equals(sound.value().location())).orElse(false);
        LivingEntity target=null;
        // Include equipment-confirmed reports so a following sound corroborates rather than duplicates them.
        var candidates=new ArrayList<CombatAttempt>();
        for(var entry:DamageHud.HISTORY.entries())if(!entry.cancelled&&now<entry.expires)candidates.add(entry.value);
        for(var report:reports)if(!report.evidence.cancelled&&!report.soundSeen&&report.attack!=null
                &&now-report.evidence.received<=report.attack.retention)candidates.add(report.attack);
        for(var a:candidates) {
            if(!a.contact||!a.shieldWeapon()||a.shields.stream().noneMatch(defense))continue;
            a.samplePosition(now);
            if(entityId!=null?a.target.getId()!=entityId:position==null||!a.near(position))continue;
            if(target!=null&&target!=a.target)return; // A positional sound supplies no attacker or entity identity.
            target=a.target;
        }
        if(target==null)return;
        for(var r:reports)if(r.target==target&&!r.soundSeen&&!r.evidence.cancelled
                &&now-r.evidence.received<=r.evidence.candidates.getFirst().value.retention
                &&r.evidence.candidates.stream().anyMatch(e->e.value.shields.stream().anyMatch(defense))) {
            r.soundSeen=true;return;
        }
        var report=create(target.getUUID(),defense,null,now);
        if(report!=null)report.soundSeen=true;
    }
    private static boolean sameShield(ItemStack first,ItemStack second) {
        if(first.isEmpty()||second.isEmpty())return false;
        ItemStack a=first.copyWithCount(1),b=second.copyWithCount(1);a.setDamageValue(0);b.setDamageValue(0);
        return ItemStack.isSameItemSameComponents(a,b);
    }
    static void equipment(LivingEntity target,EquipmentSlot slot,ItemStack before,ItemStack after) {
        if(slot!=EquipmentSlot.MAINHAND&&slot!=EquipmentSlot.OFFHAND)return;
        long now=CombatTiming.now();DamageHud.HISTORY.expire(now);
        var key=new ShieldKey(target.getUUID(),slot);
        serverShields.put(key,(after.isEmpty()?before:after).copy());
        if(!before.isDamageableItem()||after.isEmpty())return;
        int loss=after.getDamageValue()-before.getDamageValue();
        if(!sameShield(before,after)||loss<0) {
            for(var r:reports)if(r.target==target&&r.matches(slot,before))cancel(r);
            for(var e:DamageHud.HISTORY.entries())if(e.value.target==target&&e.value.shields.stream().anyMatch(s->s.slot()==slot&&sameShield(s.shield(),before)))e.cancelled=true;
            return;
        }
        if(loss<=0)return;
        var batch=new ArrayList<Report>();
        for(var r:reports)if(r.target==target&&!r.equipmentSeen&&!r.evidence.cancelled&&r.matches(slot,before))batch.add(r);
        var possible=DamageHud.HISTORY.entries().stream().filter(e->!e.cancelled&&now<e.expires&&e.value.target==target&&e.value.contact&&e.value.shieldWeapon()
                &&e.value.shields.stream().anyMatch(s->s.slot()==slot&&sameShield(s.shield(),before))).toList();
        int existing=(int)batch.stream().filter(r->r.attack==null).count();
        double budget=loss-batch.stream().mapToDouble(Report::weight).sum();
        // Wear can confirm contacts with different cooldown/fall snapshots. Treat the packet's
        // delta as one measured batch; predicted costs only apportion it, never gate measurement.
        for(int i=existing;i<possible.size()&&budget>0;i++) {
            var candidate=possible.get(i).value;
            var shield=candidate.shields.stream().filter(s->s.slot()==slot&&sameShield(s.shield(),before)).findFirst().orElseThrow().confirmed();
            var r=create(target.getUUID(),s->s.slot()==slot&&sameShield(s.shield(),before),slot,now);
            if(r!=null)batch.add(r);
            budget-=Math.max(1,shield.durability(candidate.raw,false));
        }
        if(batch.isEmpty())return;
        double weight=batch.stream().mapToDouble(Report::weight).sum(),left=loss;
        for(int i=0;i<batch.size();i++) {
            var r=batch.get(i);r.slot=slot;r.equipmentSeen=true;
            r.measured=i==batch.size()-1?left:loss*r.weight()/weight;left-=r.measured;
            // The equipment slot and item are server evidence that can narrow a preceding sound.
            if(r.attack==null)r.evidence.candidates.removeIf(e->e.value.shields.stream().noneMatch(s->s.slot()==slot&&sameShield(s.shield(),before)&&r.defense.test(s)));
            if(r.attack!=null&&r.applied&&!r.attack.calculated)correct(r,r.measured);
        }
    }
    static void resolved(AttackHistory.Resolution<CombatAttempt> result) {
        var report=reports.stream().filter(r->r.evidence==result.evidence()).findFirst().orElse(null);
        if(report==null)return;
        report.attack=result.entry().value;report.shield=report.shield(report.attack);
        if(report.shield==null){cancel(report);return;}
        report.slot=report.shield.slot();
        double health=report.attack.afterGear(report.shield.remaining(report.attack.raw));
        DamageHud.queueHealth(report.attack,health,result.evidence().received);
    }
    static void finalizeResults(long now) {
        for(var r:reports) {
            if(r.evidence.cancelled||r.attack==null||r.applied)continue;
            if(!r.attack.type.enabled(SylvyesPvPHud.DAMAGE_CONFIG.current()))continue;
            if(r.attack.calculated)record(r,r.shield.durability(r.attack.raw,r.attack.useGear));
            else if(r.equipmentSeen)record(r,r.measured);
            else if(now-r.evidence.received>=r.attack.retention)record(r,r.shield.durability(r.attack.raw,false));
        }
        for(var r:reports)if(r.broken&&!r.breakApplied&&r.attack!=null&&r.applied) {
            boolean waiting=reports.stream().anyMatch(other->other.target==r.target&&other.slot==r.slot&&other.broken&&!other.applied&&!other.evidence.cancelled);
            if(!waiting){TRACKER.broken(r.target.getUUID());HITS.broken(r.target.getUUID());
                for(var other:reports)if(other.target==r.target&&other.slot==r.slot&&other.broken){other.breakApplied=true;other.soundSeen=true;}}
        }
    }
    private static void record(Report r,double amount) {
        r.applied=true;r.recordedAmount=amount;
        var c=SylvyesPvPHud.DAMAGE_CONFIG.current();
        if(!enabled(c)||!r.attack.type.enabled(c)||!Double.isFinite(amount)||amount<=0)return;
        var a=r.attack;UUID id=a.target.getUUID();String name=a.target.getName().getString();
        if(c.shieldTrackerEnabled()&&a.target instanceof Player)r.trackerSession=TRACKER.record(id,name,amount,c.trackerSeconds()*20,c.trackerHudSeconds()*20,c.trackerHudAlwaysVisible());
        if(c.hitEnabled()&&c.hitShieldEnabled()&&r.hitAllowed&&r.shield.remaining(a.raw)<=0&&DamageHud.allowDisplay(a.sequence)) {
            r.hitSession=HITS.record(id,name,amount,c.hitSeconds()*20,c.hitSeconds()*20,false);
            hitBlocks=a.blocks;DamageHud.clearHealthDisplay();shieldHitVisible=true;
        }
    }
    private static void correct(Report r,double amount) {
        double difference=amount-r.recordedAmount;
        TRACKER.adjust(r.target.getUUID(),r.trackerSession,difference);HITS.adjust(r.target.getUUID(),r.hitSession,difference);
        r.recordedAmount=amount;
    }
    static void broken(LivingEntity target,EquipmentSlot slot) {
        var key=new ShieldKey(target.getUUID(),slot);
        ItemStack known=serverShields.getOrDefault(key,target.getItemBySlot(slot));
        if(known.get(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)==null)return;
        long now=CombatTiming.now();DamageHud.HISTORY.expire(now);
        var batch=new ArrayList<Report>();
        for(var r:reports)if(r.target==target&&!r.evidence.cancelled&&!r.equipmentSeen&&r.matches(slot,known))batch.add(r);
        if(batch.isEmpty()) {
            var r=create(target.getUUID(),s->s.slot()==slot&&sameShield(s.shield(),known),slot,now);
            if(r!=null)batch.add(r);
        }
        double remaining=Math.max(0,known.getMaxDamage()-known.getDamageValue());
        double weight=batch.stream().mapToDouble(Report::weight).sum(),left=remaining;
        for(int i=0;i<batch.size();i++) {
            var r=batch.get(i);r.slot=slot;r.broken=true;r.equipmentSeen=true;
            r.measured=i==batch.size()-1?left:remaining*r.weight()/weight;left-=r.measured;
            if(r.attack!=null&&r.applied&&!r.attack.calculated)correct(r,r.measured);
        }
        for(var r:reports)if(r.target==target&&r.matches(slot,known))r.broken=true;
        serverShields.remove(key);
    }
    static void healthHit(UUID id) {
        for(var r:reports)if(r.target.getUUID().equals(id))r.hitAllowed=false;
        HITS.reset(id);HITS.dismiss();shieldHitVisible=false;
    }
    static void otherAttacker(int entityId) {
        for(var r:reports)if(r.target.getId()==entityId&&!r.equipmentSeen)cancel(r);
    }
    static void died(UUID id) {
        TRACKER.reset(id);HITS.reset(id);
        reports.removeIf(r->{if(!r.target.getUUID().equals(id))return false;cancel(r);return true;});
        serverShields.keySet().removeIf(k->k.target().equals(id));
    }
    static void tick() {
        TRACKER.tick();HITS.tick();long now=CombatTiming.now();
        reports.removeIf(r->r.evidence.cancelled||r.attack!=null&&(r.attack.target.isRemoved()||!r.attack.type.enabled(SylvyesPvPHud.DAMAGE_CONFIG.current())
                ||now-r.evidence.received>r.attack.retention&&r.applied&&(r.attack.calculated||r.equipmentSeen
                ||!TRACKER.retains(r.target.getUUID(),r.trackerSession)&&!HITS.retains(r.target.getUUID(),r.hitSession))));
        var c=SylvyesPvPHud.DAMAGE_CONFIG.current();
        if(!c.enabled()){reset();return;}
        if(!c.shieldTrackerEnabled())TRACKER.resetAll();
        if(!c.hitEnabled()||!c.hitShieldEnabled()){HITS.resetAll();shieldHitVisible=false;}
        // Evidence remains active even when both shield displays are disabled.
        serverShields.keySet().removeIf(key->reports.stream().noneMatch(r->r.target.getUUID().equals(key.target())&&(r.slot==null||r.slot==key.slot()))
                &&DamageHud.HISTORY.entries().stream().noneMatch(e->e.target.equals(key.target())));
    }
    static boolean hitVisible(){return shieldHitVisible&&Double.isFinite(HITS.amount());}
    static String hitText(DamageConfig c){return DamageText.format(c.hitShieldTemplate(),hitBlocks,HITS.amount());}
    static void resetTracker(){TRACKER.resetAll();}
    static void reset(){for(var r:reports)cancel(r);reports.clear();serverShields.clear();TRACKER.resetAll();HITS.resetAll();shieldHitVisible=false;}
    private ShieldDamageHud(){}
}
