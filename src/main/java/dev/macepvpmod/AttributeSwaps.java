package dev.macepvpmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.core.component.DataComponents;

public final class AttributeSwaps {
    private static final AttributeSwapTracker TRACKER = new AttributeSwapTracker();
    static final String HUD_TEXT = "Attribute swap!";
    private static int displayTicks;
    private static Object player;
    private static Object level;
    private static boolean active(Minecraft mc) {
        if (player != mc.player || level != mc.level) {
            displayTicks = 0; TRACKER.reset(); player = mc.player; level = mc.level;
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
    public static void successfulHit() {
        Minecraft mc = Minecraft.getInstance();
        if (active(mc)) TRACKER.successfulHit();
    }
    public static void selected(Inventory inventory, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || inventory != mc.player.getInventory()) return;
        AttributeSwapConfig config = MacePvPMod.ATTRIBUTE_SWAP_CONFIG.current();
        boolean weapon = !config.weaponOnly() || inventory.getItem(slot).has(DataComponents.WEAPON);
        if (active(mc) && TRACKER.select(slot, config.successfulHitOnly(), weapon)) {
            if (config.visualEnabled()) displayTicks = 60;
            if (config.soundEnabled()) playSound(config.soundId());
        }
    }
    public static void endTick() {
        TRACKER.reset();
        var mc = Minecraft.getInstance();
        if (player != mc.player || level != mc.level || mc.player == null || !mc.player.isAlive()
                || mc.player.isSpectator() || !MacePvPMod.ATTRIBUTE_SWAP_CONFIG.current().visualEnabled()) {
            displayTicks = 0;
            player = mc.player; level = mc.level;
        } else if (!mc.isPaused() && displayTicks > 0) displayTicks--;
    }
    static boolean shouldRender(Minecraft mc) {
        return displayTicks > 0 && player == mc.player && level == mc.level && mc.player != null && mc.level != null
                && mc.player.isAlive() && !mc.player.isSpectator() && mc.gui.screen() == null
                && !mc.gui.hud.isHidden() && MacePvPMod.ATTRIBUTE_SWAP_CONFIG.current().visualEnabled();
    }
    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (shouldRender(Minecraft.getInstance())) HudRenderer.text(g, HUD_TEXT, MacePvPMod.HUD_CONFIG.current().attributeSwap(), 0);
    }
    public static boolean validSound(String value) {
        Identifier id = Identifier.tryParse(value);
        return id != null && BuiltInRegistries.SOUND_EVENT.containsKey(id);
    }
    public static void playSound(String value) {
        if (validSound(value)) Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(value)), 1.0f));
    }
}
