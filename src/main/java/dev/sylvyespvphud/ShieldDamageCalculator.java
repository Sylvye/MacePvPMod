package dev.sylvyespvphud;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

/** The read-only part of LivingEntity.applyItemBlocking, captured before the server response. */
final class ShieldDamageCalculator {
    private ShieldDamageCalculator() {}

    record Snapshot(BlocksAttacks blocking, DamageSource source, double angle, ItemStack shield, boolean player) {
        static final Snapshot NONE = new Snapshot(null, null, 0, ItemStack.EMPTY, false);

        double durability(double damage, boolean useGear) {
            if (!Double.isFinite(damage) || damage <= 0 || blocking == null || !player || !shield.isDamageableItem()) return 0;
            double blocked = damage - remaining(damage);
            if (blocked <= 0) return 0;
            int cost = blocking.itemDamage().apply((float) blocked);
            int unbreaking = useGear ? MaceDamageCalculator.enchantmentLevel(shield, Enchantments.UNBREAKING) : 0;
            return Math.max(0,Math.min(shield.getMaxDamage()-shield.getDamageValue(),expectedDurability(cost, unbreaking)));
        }

        double remaining(double damage) {
            if (blocking == null || damage <= 0) return damage;
            float incoming = (float) damage;
            float blocked = blocking.resolveBlockedDamage(source, incoming, angle);
            return blocked >= incoming ? 0 : Math.max(0, damage - blocked);
        }
    }

    static double expectedDurability(int cost, int unbreaking) {
        return Math.max(0, cost) / (1.0 + Math.max(0, unbreaking));
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
        return new Snapshot(blocking, source, angle, item.copy(), target instanceof Player);
    }
}
