package dev.macepvpmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Items;

public final class SurvivalHud {
    private static boolean hasTotem;
    private SurvivalHud() {}

    public static void tick(Minecraft mc) {
        // Recheck while inventory screens are open, and after pickups, drops or totem use.
        // This also initializes correctly when joining with an existing inventory.
        SurvivalAudio.tick(mc);
        hasTotem = false;
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) return;
        var inventory = mc.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(Items.TOTEM_OF_UNDYING)) {
                hasTotem = true;
                break;
            }
        }
    }

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance(); var p = mc.player;
        if (p == null || mc.level == null || mc.gui.hud.isHidden() || mc.gui.screen() != null
                || !p.isAlive() || p.isSpectator()) return;
        var c = MacePvPMod.SURVIVAL_CONFIG.current();
        if (c.retotemEnabled() && SurvivalState.needsRetotem(hasTotem, p.getOffhandItem().isEmpty()))
            draw(g, c.retotemText(), c.retotemColor(), c.retotemSize(), c.retotemX(), c.retotemY());
        int state = SurvivalState.healingState(p.getHealth(), p.getMaxHealth(), p.getFoodData().getSaturationLevel(), c);
        if (c.healingEnabled() && state != 0 && (p.tickCount % 20 < 14)) drawHealing(g, c, state);
    }

    static void drawHealing(GuiGraphicsExtractor g, SurvivalConfig c, int state) {
        draw(g, state == 3 ? c.combinedText() : state == 1 ? c.healthText() : c.saturationText(),
                state == 3 ? c.combinedColor() : state == 1 ? c.healthColor() : c.saturationColor(),
                c.healingSize(), c.healingX(), c.healingY());
    }

    static void draw(GuiGraphicsExtractor g, String text, int color, double size, double x, double y) {
        var font = Minecraft.getInstance().font;
        float scale = (float)Math.min(size, Math.max(1, g.guiWidth() - 8) / (double)Math.max(1, font.width(text)));
        float px = (float)((g.guiWidth() - font.width(text) * scale) * x / 100);
        float py = (float)((g.guiHeight() - font.lineHeight * scale) * y / 100);
        g.pose().pushMatrix();
        g.pose().translate(px, py); g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, 0xff000000 | color);
        g.pose().popMatrix();
    }
}
