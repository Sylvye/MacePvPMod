package dev.macepvpmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public final class AttributeSwaps {
    private static final AttributeSwapTracker TRACKER = new AttributeSwapTracker();
    private static Object player;
    private static Object level;
    private static boolean active(Minecraft mc) {
        if (player != mc.player || level != mc.level) {
            TRACKER.reset(); player = mc.player; level = mc.level;
        }
        if (mc.player == null || mc.level == null || mc.gui.screen() != null || mc.isPaused()) {
            TRACKER.reset(); return false;
        }
        return true;
    }
    public static void click() {
        Minecraft mc = Minecraft.getInstance();
        if (active(mc)) TRACKER.click(mc.player.getInventory().getSelectedSlot());
    }
    public static void selected(Inventory inventory, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || inventory != mc.player.getInventory()) return;
        if (active(mc) && TRACKER.select(slot)) {
            AttributeSwapConfig config = MacePvPMod.ATTRIBUTE_SWAP_CONFIG.current();
            if (config.visualEnabled()) mc.player.sendOverlayMessage(Component.literal("Attribute swap!"));
            if (config.soundEnabled()) playSound(config.soundId());
        }
    }
    public static void endTick() { TRACKER.reset(); }
    public static boolean validSound(String value) {
        Identifier id = Identifier.tryParse(value);
        return id != null && BuiltInRegistries.SOUND_EVENT.containsKey(id);
    }
    public static void playSound(String value) {
        if (validSound(value)) Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(value)), 1.0f));
    }
}
