package dev.macepvpmod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;

final class MaceDamageCalculator {
    private MaceDamageCalculator() {}
    static double effectiveAttackDamage(Player player) {
        double base = player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        ItemAttributeModifiers modifiers = player.getMainHandItem().getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return modifiers.compute(Attributes.ATTACK_DAMAGE, base, EquipmentSlot.MAINHAND);
    }
    static int enchantmentLevel(net.minecraft.world.item.ItemStack stack,
                                net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        for (var entry : stack.getEnchantments().entrySet())
            if (entry.getKey().is(enchantment)) return entry.getIntValue();
        return 0;
    }
    static double atAttack(Player player) { return atAttack(player, null); }
    static double atAttack(Player player, LivingEntity target) {
        var stack = player.getMainHandItem();
        int density = enchantmentLevel(stack, Enchantments.DENSITY);
        double cooldown = AttributeSwaps.attackCooldown(net.minecraft.client.Minecraft.getInstance());
        boolean critical = player.fallDistance > 0 && !player.onGround() && !player.onClimbable()
                && !player.isInWater() && !player.isMobilityRestricted()
                && !player.isPassenger() && !player.isSprinting();
        double base = AttributeSwaps.attackDamage(net.minecraft.client.Minecraft.getInstance());
        double damage;
        if (DamageWeapon.of(stack) == DamageWeapon.MACE) {
            damage = MaceDamageMath.calculate(base, cooldown, player.fallDistance,
                    player.isFallFlying(), density, critical);
        } else {
            double charge = Math.max(0, Math.min(1, cooldown));
            damage = base * (0.2 + 0.8 * charge * charge) * (charge > .9 && critical ? 1.5 : 1);
        }
        damage += enchantmentBonus(stack, target) * Math.max(0, Math.min(1, cooldown));
        return damage;
    }
    static double enchantmentBonus(net.minecraft.world.item.ItemStack stack, LivingEntity target) {
        int sharpness = enchantmentLevel(stack, Enchantments.SHARPNESS);
        double result = sharpness == 0 ? 0 : 0.5 * sharpness + 0.5;
        int smite = enchantmentLevel(stack, Enchantments.SMITE);
        if (target != null && target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_SMITE)) result += 2.5 * smite;
        int bane = enchantmentLevel(stack, Enchantments.BANE_OF_ARTHROPODS);
        if (target != null && target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) result += 2.5 * bane;
        int impaling = enchantmentLevel(stack, Enchantments.IMPALING);
        if (target != null && target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_IMPALING)) result += 2.5 * impaling;
        return result;
    }
    static double afterGear(double damage, net.minecraft.world.item.ItemStack weapon, LivingEntity target) {
        float armor = target.getArmorValue();
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        int protection = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            protection += enchantmentLevel(target.getItemBySlot(slot), Enchantments.PROTECTION);
        return afterGear(damage, armor, toughness, enchantmentLevel(weapon, Enchantments.BREACH), protection);
    }
    static double afterGear(double damage, float armor, float toughness, int breach, int protection) {
        float ratio = Math.max(armor * .2f, Math.min(20, armor - (float) damage / (2 + toughness / 4))) / 25;
        ratio = Math.max(0, ratio - .15f * Math.max(0, breach));
        return damage * (1 - ratio) * (1 - Math.min(20, Math.max(0, protection)) / 25.0);
    }
}
