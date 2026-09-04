package dev.macepvpmod;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.entity.player.Player;

final class SurvivalItems {
    record Availability(boolean totem, boolean healing, boolean saturation) {}
    static SurvivalItemRule rule(ItemStack stack) {
        var contents = stack.get(DataComponents.POTION_CONTENTS);
        String potion = contents == null ? null : contents.potion().flatMap(p -> p.unwrapKey()).map(k -> k.identifier().toString()).orElse(null);
        return new SurvivalItemRule(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), potion);
    }
    static Availability scan(Player player, SurvivalConfig config) {
        var healing = new HashSet<>(config.healingItems());
        var saturation = new HashSet<>(config.saturationItems());
        boolean totem = false, heal = false, food = false;
        for (ItemStack stack : carried(player)) {
            if (stack.isEmpty()) continue;
            totem |= stack.is(Items.TOTEM_OF_UNDYING);
            var key = rule(stack);
            heal |= healing.contains(key); food |= saturation.contains(key);
        }
        return new Availability(totem, heal, food);
    }
    static List<ItemStack> carried(Player player) {
        var stacks = new ArrayList<ItemStack>();
        if (player == null) return stacks;
        for (int slot = 0; slot < 36; slot++) stacks.add(player.getInventory().getItem(slot));
        stacks.add(player.getOffhandItem());
        return stacks;
    }
    static ItemStack icon(SurvivalItemRule rule) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(rule.item()));
        if (item == null || item == Items.AIR) return new ItemStack(Items.BARRIER);
        if (rule.potion() == null) return new ItemStack(item);
        var potion = BuiltInRegistries.POTION.get(Identifier.parse(rule.potion()));
        return potion.map(p -> PotionContents.createItemStack(item, p)).orElseGet(() -> new ItemStack(Items.BARRIER));
    }
    static String name(SurvivalItemRule rule) {
        var icon = icon(rule);
        String name = icon.getHoverName().getString();
        // Potion display names omit strength/duration; include the type to distinguish variants.
        return name + (rule.potion() == null ? "" : " (" + rule.potion().split(":", 2)[1].replace('_', ' ') + ")");
    }
    static List<SurvivalItemRule> catalog() {
        var result = new LinkedHashSet<SurvivalItemRule>();
        for (var item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            if (item instanceof PotionItem || item == Items.TIPPED_ARROW) {
                for (var potion : BuiltInRegistries.POTION.keySet())
                    result.add(new SurvivalItemRule(BuiltInRegistries.ITEM.getKey(item).toString(), potion.toString()));
            } else result.add(rule(new ItemStack(item)));
        }
        return new ArrayList<>(result);
    }
}
