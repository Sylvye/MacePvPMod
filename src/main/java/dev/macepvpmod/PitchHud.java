package dev.macepvpmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class PitchHud {
    private PitchHud() {}
    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        PitchConfig c = MacePvPMod.CONFIG.current();
        if (!shouldRender(mc, c)) return;
        draw(graphics, c, graphics.guiWidth() / 2, graphics.guiHeight() / 2,
                p.getXRot(delta.getGameTimeDeltaPartialTick(false)), graphics.guiWidth(), graphics.guiHeight());
    }
    static boolean shouldRender(Minecraft mc, PitchConfig c) {
        var p = mc.player;
        return !(!c.enabled() || p == null || mc.level == null || mc.gui.screen() != null || mc.gui.hud.isHidden()
                || !p.isAlive() || p.isSpectator() || p.onGround() || !p.isFallFlying()
                || (!c.thirdPerson() && !mc.options.getCameraType().isFirstPerson()));
    }
    static void draw(GuiGraphicsExtractor graphics, PitchConfig c, int centerX, int centerY,
                     double pitch, int availableWidth, int availableHeight) {
        int lineWidth = Math.min(c.width(), Math.max(1, availableWidth - 8));
        int limit = Math.max(0, availableHeight / 2 - c.thickness() - 4);
        int offset = (int) Math.round(Math.max(-limit, Math.min(limit, PitchMath.offset(pitch, c))));
        int left = centerX - lineWidth / 2;
        int top = centerY + offset - c.thickness() / 2;
        graphics.fill(left, top, left + lineWidth, top + c.thickness(), c.argb());
    }
}
