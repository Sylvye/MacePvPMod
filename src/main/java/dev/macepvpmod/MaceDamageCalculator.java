package dev.macepvpmod;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantments;

final class MaceDamageCalculator {
    private MaceDamageCalculator() {}
    static double atAttack(Player player) {
        int density = 0;
        for (var entry : player.getMainHandItem().getEnchantments().entrySet()) {
            if (entry.getKey().is(Enchantments.DENSITY)) density = entry.getIntValue();
        }
        boolean critical = player.fallDistance > 0 && !player.onGround() && !player.onClimbable()
                && !player.isInWater() && !player.isMobilityRestricted()
                && !player.isPassenger() && !player.isSprinting();
        // Snapshot before the local attack resets its cooldown or the server resets the fall.
        return MaceDamageMath.calculate(AttributeSwaps.attackDamage(net.minecraft.client.Minecraft.getInstance()),
                AttributeSwaps.attackCooldown(net.minecraft.client.Minecraft.getInstance()), player.fallDistance,
                player.isFallFlying(), density, critical);
    }
}
