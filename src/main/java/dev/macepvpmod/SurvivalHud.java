package dev.macepvpmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Items;

public final class SurvivalHud {
    private static SurvivalItems.Availability available = new SurvivalItems.Availability(false, false, false);
    private static float lastVitals = Float.NaN;
    private static int damageEffectTicks;
    private SurvivalHud() {}

    public static void tick(Minecraft mc) {
        // Recheck while inventory screens are open, and after pickups, drops or totem use.
        // This also initializes correctly when joining with an existing inventory.
        SurvivalAudio.tick(mc);
        available = new SurvivalItems.Availability(false, false, false);
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) { lastVitals = Float.NaN; damageEffectTicks = 0; return; }
        float vitals = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (!Float.isNaN(lastVitals) && vitals < lastVitals) damageEffectTicks = 12;
        else if (damageEffectTicks > 0) damageEffectTicks--;
        lastVitals = vitals;
        available = SurvivalItems.scan(mc.player, MacePvPMod.SURVIVAL_CONFIG.current());
    }

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance(); var p = mc.player;
        if (p == null || mc.level == null || mc.gui.hud.isHidden() || mc.gui.screen() != null
                || !p.isAlive() || p.isSpectator()) return;
        var c = MacePvPMod.SURVIVAL_CONFIG.current();
        if (!c.enabled()) return;
        if (c.damageIndicatorEnabled() && damageEffectTicks > 0) drawDamageIndicator(g, c);
        if (c.retotemEnabled() && SurvivalState.needsRetotem(available.totem(), p.getOffhandItem().isEmpty()))
            HudRenderer.text(g, c.retotemText(), MacePvPMod.HUD_CONFIG.current().retotem(), 0);
        int state = SurvivalState.healingState(p.getHealth(), p.getMaxHealth(), p.getFoodData().getSaturationLevel(), c);
        state = SurvivalState.actionableState(state, available.healing(), available.saturation());
        if (c.healingEnabled() && state != 0 && (p.tickCount % 20 < 14)) drawHealing(g, c, state);
    }

    static void drawDamageIndicator(GuiGraphicsExtractor g, SurvivalConfig c) {
        var window = Minecraft.getInstance().getWindow();
        int width = window.getGuiScaledWidth(), height = window.getGuiScaledHeight();
        int thickness = Math.min(c.damageIndicatorThickness(), Math.min(width, height) / 2);
        double fade = Math.min(1, damageEffectTicks / 6.0);
        int argb = ((int)Math.round(c.damageIndicatorOpacity() * fade * 255) << 24) | c.damageIndicatorColor();
        g.fill(0, 0, width, thickness, argb);
        g.fill(0, height - thickness, width, height, argb);
        g.fill(0, thickness, thickness, height - thickness, argb);
        g.fill(width - thickness, thickness, width, height - thickness, argb);
    }

    static void drawHealing(GuiGraphicsExtractor g, SurvivalConfig c, int state) {
        HudRenderer.text(g, state == 3 ? c.combinedText() : state == 1 ? c.healthText() : c.saturationText(),
                MacePvPMod.HUD_CONFIG.current().healing(), state);
    }

}
