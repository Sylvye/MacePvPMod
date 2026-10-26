package dev.sylvyespvphud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Correlates local attack snapshots with server block sounds and equipment updates. */
final class ShieldDamageHud {
    static final ShieldDamageTotals TRACKER = new ShieldDamageTotals(CumulativeDamageTracker.FADE_TICKS);
    static final ShieldDamageTotals HITS = new ShieldDamageTotals(0);
    private static final List<Attempt> attempts = new ArrayList<>();
    private static final int CONFIRMATION_TICKS=40;
    private record ShieldKey(UUID target,EquipmentSlot slot) {}
    private static final Map<ShieldKey,ItemStack> serverShields=new HashMap<>();
    private static int clock;
    private static boolean shieldHitVisible;
    private static double hitBlocks;

    static final class Attempt {
        final LivingEntity target;
        final ShieldDamageCalculator.Snapshot shield;
        final Object token;
        final double cost, rawCost, healthDamage, blocks;
        final EquipmentSlot slot;
        final DamageWeapon weapon;
        final double healthBefore;
        final int created;
        boolean contact, confirmed, equipmentSeen, soundSeen, broken;
        final boolean calculated;
        boolean applied, hitAllowed=true;
        int equipmentAt, confirmedAt;
        double recordedAmount;
        Object trackerSession, hitSession;
        Attempt(LivingEntity target, ShieldDamageCalculator.Snapshot shield, double raw, ItemStack weaponStack, Object token, boolean contact) {
            this.target = target; this.shield = shield; this.token = token; this.contact = contact;
            var config = SylvyesPvPHud.DAMAGE_CONFIG.current();
            calculated=config.calculatedDamage();
            weapon = DamageWeapon.of(weaponStack);
            healthBefore = target.getHealth();
            cost = shield.durability(raw, config.calculatedDamage() && config.useEnemyGear());
            rawCost = shield.durability(raw, false);
            double remaining=shield.remaining(raw);
            healthDamage = config.calculatedDamage() && config.useEnemyGear() ? MaceDamageCalculator.afterGear(remaining,weaponStack,target) : remaining;
            blocks = Minecraft.getInstance().player.fallDistance;
            slot = target.getUsedItemHand().asEquipmentSlot();
            created = clock;
        }
    }

    static boolean enabled(DamageConfig c) { return c.enabled() && (c.shieldTrackerEnabled() || c.hitEnabled() && c.hitShieldEnabled()); }

    static Attempt prepare(LivingEntity target, ShieldDamageCalculator.Snapshot shield, double raw,
                           ItemStack weapon, Object token, boolean contact) {
        var config = SylvyesPvPHud.DAMAGE_CONFIG.current();
        DamageWeapon type = DamageWeapon.of(weapon);
        if (!enabled(config) || !type.enabled(config) || type != DamageWeapon.MACE && type != DamageWeapon.SPEAR
                || (config.calculatedDamage()?shield.durability(raw,false)<=0:!shield.player()||!shield.shield().isDamageableItem()||!Double.isFinite(raw)||raw<=0||shield.remaining(raw)>=raw)) return null;
        for (Attempt previous : attempts) if (previous.target == target && previous.token == token) {
            previous.contact |= contact;
            return previous;
        }
        Attempt attempt = new Attempt(target, shield, raw, weapon, token, contact);
        attempts.add(attempt);
        serverShields.put(new ShieldKey(target.getUUID(),attempt.slot),shield.shield().copy());
        return attempt;
    }

    static void confirm(Attempt attempt) {
        if (attempt == null || attempt.confirmed) return;
        attempt.confirmed = true;
        attempt.confirmedAt=clock;
        var c = SylvyesPvPHud.DAMAGE_CONFIG.current();
        if (!enabled(c) || !attempt.weapon.enabled(c)) return;
        // A block sound proves contact, not how much durability the server removed.
        if(attempt.calculated)record(attempt,attempt.cost);
        if(attempt.healthDamage>0)DamageHud.shieldHealthHit(attempt);
    }

    private static void record(Attempt attempt,double amount) {
        var c=SylvyesPvPHud.DAMAGE_CONFIG.current();
        if(!enabled(c)||!attempt.weapon.enabled(c)||amount<=0||!Double.isFinite(amount))return;
        UUID id=attempt.target.getUUID();String name=attempt.target.getName().getString();
        attempt.applied=true;attempt.recordedAmount=amount;
        if(c.shieldTrackerEnabled()&&attempt.target instanceof Player)
            attempt.trackerSession=TRACKER.record(id,name,amount,c.trackerSeconds()*20,c.trackerHudSeconds()*20,c.trackerHudAlwaysVisible());
        if(c.hitEnabled()&&c.hitShieldEnabled()&&attempt.hitAllowed&&attempt.healthDamage<=0) {
            attempt.hitSession=HITS.record(id,name,amount,c.hitSeconds()*20,c.hitSeconds()*20,false);
            hitBlocks=attempt.blocks;DamageHud.clearHealthDisplay();shieldHitVisible=true;
        }
    }

    /** Every batch contributes precisely the observed delta; estimates only apportion late corrections. */
    private static void measured(List<Attempt> batch,double loss) {
        if(batch.isEmpty()||loss<=0)return;
        double weight=batch.stream().mapToDouble(a->Math.max(1,a.rawCost)).sum();
        double left=loss;
        for(int i=0;i<batch.size();i++) {
            Attempt a=batch.get(i);
            double amount=i==batch.size()-1?left:loss*Math.max(1,a.rawCost)/weight;left-=amount;
            confirm(a);
            if(a.applied) {
                double correction=amount-a.recordedAmount;
                TRACKER.adjust(a.target.getUUID(),a.trackerSession,correction);
                HITS.adjust(a.target.getUUID(),a.hitSession,correction);
                a.recordedAmount=amount;
            } else record(a,amount);
            a.equipmentSeen=true;a.equipmentAt=clock;
        }
    }

    static void healthHit(UUID id) {
        for(Attempt a:attempts)if(a.target.getUUID().equals(id))a.hitAllowed=false;
        HITS.reset(id);
        HITS.dismiss();
        shieldHitVisible = false;
    }

    static void blockSound(Holder<SoundEvent> sound, Vec3 position, Integer entityId) {
        Attempt match = null;
        for (Attempt attempt : attempts) {
            if (!attempt.contact || attempt.soundSeen || clock - attempt.created > CONFIRMATION_TICKS
                    || attempt.confirmed && (!attempt.equipmentSeen || clock-attempt.equipmentAt>2)) continue;
            if (entityId != null ? attempt.target.getId() != entityId : attempt.target.position().distanceToSqr(position) > .25) continue;
            if (attempt.shield.blocking().blockSound().isEmpty()
                    || !attempt.shield.blocking().blockSound().get().value().location().equals(sound.value().location())) continue;
            // Positional sounds identify no attacker. Require one matching local target.
            if (match != null && match.target != attempt.target) return;
            if (match == null || match.confirmed && !attempt.confirmed
                    || match.confirmed && attempt.confirmed && attempt.created>=match.created) match = attempt;
        }
        if(match!=null)match.soundSeen=true;
        confirm(match);
    }

    private static boolean sameShield(ItemStack first,ItemStack second) {
        if(first.isEmpty()||second.isEmpty())return false;
        ItemStack a=first.copyWithCount(1),b=second.copyWithCount(1);
        a.setDamageValue(0);b.setDamageValue(0);
        return ItemStack.isSameItemSameComponents(a,b);
    }

    static void equipment(LivingEntity target, EquipmentSlot slot, ItemStack before, ItemStack after) {
        if(slot!=EquipmentSlot.MAINHAND&&slot!=EquipmentSlot.OFFHAND)return;
        ShieldKey key=new ShieldKey(target.getUUID(),slot);
        // Preserve the last known remaining durability when removal precedes the break event.
        if(serverShields.containsKey(key))serverShields.put(key,(after.isEmpty()?before:after).copy());
        if(!before.isDamageableItem())return;
        if(after.isEmpty())return; // Only a break event can distinguish breakage from unequipping.
        int loss=after.getDamageValue()-before.getDamageValue();
        if(!sameShield(before,after)||loss<0) {
            attempts.removeIf(a->!a.calculated&&a.target==target&&a.slot==slot);
            return;
        }
        if(loss<=0)return;
        List<Attempt> reported=new ArrayList<>();
        double unmatched=loss;
        for(Attempt a:attempts) {
            if(a.calculated||a.target!=target||a.slot!=slot||!a.contact||!sameShield(a.shield.shield(),before))continue;
            if(!a.equipmentSeen&&(a.confirmed||unmatched>0)){reported.add(a);unmatched-=Math.max(1,a.rawCost);}
        }
        if(!reported.isEmpty())measured(reported,loss);
        // Preserve Calculated mode's estimate and its existing confirmation/deduplication behavior.
        double budget=loss;
        for(Attempt a:attempts) {
            if(!a.calculated||a.target!=target||a.slot!=slot||!a.contact||a.equipmentSeen||!sameShield(a.shield.shield(),before))continue;
            if(budget<=0)break;
            a.equipmentSeen=true;a.equipmentAt=clock;
            budget-=Math.min(a.rawCost,Math.max(1,a.cost));confirm(a);
        }
    }

    static void broken(LivingEntity target, EquipmentSlot slot) {
        Attempt last = null;
        for (Attempt attempt : attempts) if (attempt.target == target && attempt.slot == slot && attempt.contact) last = attempt;
        if (last == null) return; // A non-shield item breaking must not reset shield totals.
        ItemStack held=target.getItemBySlot(slot);
        if(!held.isEmpty()&&!ItemStack.isSameItem(held,last.shield.shield()))return;
        last.broken = true;
        if(last.calculated)confirm(last);
        else if(!last.equipmentSeen) {
            ShieldKey key=new ShieldKey(target.getUUID(),slot);
            ItemStack known=held.isEmpty()?serverShields.getOrDefault(key,last.shield.shield()):held;
            if(sameShield(known,last.shield.shield())) {
                double remaining=Math.max(0,known.getMaxDamage()-known.getDamageValue());
                List<Attempt> batch=new ArrayList<>();
                for(Attempt a:attempts)if(!a.calculated&&a.target==target&&a.slot==slot&&a.contact&&!a.equipmentSeen&&sameShield(a.shield.shield(),known))batch.add(a);
                measured(batch,remaining);
            }
        }
        serverShields.remove(new ShieldKey(target.getUUID(),slot));
        TRACKER.broken(target.getUUID());
        HITS.broken(target.getUUID());
        // Late sounds/equipment for the broken item must not consume a replacement shield's first hit.
        attempts.removeIf(a -> a.target==target && a.slot==slot);
    }

    static void unblocked(Attempt attempt) {
        if(attempt!=null&&!attempt.confirmed){attempt.confirmed=true;attempts.remove(attempt);}
    }

    static void otherAttacker(int entityId) {
        attempts.removeIf(a -> a.target.getId()==entityId && !a.confirmed);
    }

    static void died(UUID id) {
        TRACKER.reset(id); HITS.reset(id);
        attempts.removeIf(a -> a.target.getUUID().equals(id));
        serverShields.keySet().removeIf(k->k.target().equals(id));
    }

    static void tick() {
        clock++;
        TRACKER.tick(); HITS.tick();
        for(Attempt a:attempts)if(!a.calculated&&a.confirmed&&!a.applied&&!a.equipmentSeen&&clock-a.confirmedAt>=CONFIRMATION_TICKS)record(a,a.cost);
        attempts.removeIf(a -> !a.target.isAlive() || !a.weapon.enabled(SylvyesPvPHud.DAMAGE_CONFIG.current())
                || (!a.confirmed || a.calculated || a.equipmentSeen) && clock-a.created>CONFIRMATION_TICKS
                || a.confirmed&&!a.calculated&&a.cost<=0&&clock-a.confirmedAt>CONFIRMATION_TICKS
                || !a.calculated&&a.applied&&!a.equipmentSeen&&!TRACKER.retains(a.target.getUUID(),a.trackerSession)&&!HITS.retains(a.target.getUUID(),a.hitSession));
        var c = SylvyesPvPHud.DAMAGE_CONFIG.current();
        if (!c.enabled()) { reset(); return; }
        if (!c.shieldTrackerEnabled()) TRACKER.resetAll();
        if (!c.hitEnabled() || !c.hitShieldEnabled()) { HITS.resetAll(); shieldHitVisible = false; }
        if (!enabled(c)){attempts.clear();serverShields.clear();}
    }
    static boolean hitVisible() { return shieldHitVisible && Double.isFinite(HITS.amount()); }
    static String hitText(DamageConfig c) {
        return DamageText.format(c.hitShieldTemplate(), hitBlocks, HITS.amount());
    }
    static void resetTracker() { TRACKER.resetAll(); }
    static void reset() { attempts.clear();serverShields.clear(); TRACKER.resetAll(); HITS.resetAll(); shieldHitVisible = false; }
    private ShieldDamageHud() {}
}
