package dev.macepvpmod;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

enum DamageWeapon {
    MACE, SPEAR, SWORD_AXE, OTHER;

    static DamageWeapon of(ItemStack stack) {
        if (stack.is(Items.MACE)) return MACE;
        if (stack.is(ItemTags.SPEARS)) return SPEAR;
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) return SWORD_AXE;
        return OTHER;
    }

    boolean enabled(DamageConfig config) {
        return switch (this) {
            case MACE -> config.maceEnabled();
            case SPEAR -> config.spearEnabled();
            case SWORD_AXE -> config.swordAxeEnabled();
            default -> false;
        };
    }
}
