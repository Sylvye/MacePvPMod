package dev.sylvyespvphud;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.Vec3;

/** The read-only part of LivingEntity.applyItemBlocking, captured before the server response. */
final class ShieldDamageCalculator {
    private ShieldDamageCalculator() {}

    record Snapshot(BlocksAttacks blocking, DamageSource source, double angle) {
        static final Snapshot NONE = new Snapshot(null, null, 0);

        double remaining(double damage) {
            if (blocking == null || damage <= 0) return damage;
            float incoming = (float) damage;
            float blocked = blocking.resolveBlockedDamage(source, incoming, angle);
            return blocked >= incoming ? 0 : Math.max(0, damage - blocked);
        }
    }

    static Snapshot capture(LivingEntity target, DamageSource source) {
        // Vanilla includes the use delay here. An equipped but idle shield does not block.
        var item = target.getItemBlockingWith();
        if (item == null) return Snapshot.NONE;
        var blocking = item.get(DataComponents.BLOCKS_ATTACKS);
        if (blocking == null || blocking.bypassedBy().map(types -> types.contains(source.typeHolder())).orElse(false))
            return Snapshot.NONE;
        Vec3 origin = source.getSourcePosition();
        double angle = (double) (float) Math.PI;
        if (origin != null) {
            Vec3 offset = origin.subtract(target.position());
            Vec3 direction = new Vec3(offset.x, 0, offset.z).normalize();
            Vec3 facing = target.calculateViewVector(0, target.getYHeadRot());
            angle = Math.acos(direction.dot(facing));
        }
        return new Snapshot(blocking, source, angle);
    }
}
