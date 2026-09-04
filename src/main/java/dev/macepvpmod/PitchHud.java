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
        HudRenderer.pitch(graphics, MacePvPMod.HUD_CONFIG.current().pitch(), c,
                p.getXRot(delta.getGameTimeDeltaPartialTick(false)));
    }
    static boolean shouldRender(Minecraft mc, PitchConfig c) {
        var p = mc.player;
        return !(!c.enabled() || p == null || mc.level == null || mc.gui.screen() != null || mc.gui.hud.isHidden()
                || !p.isAlive() || p.isSpectator() || p.onGround() || !p.isFallFlying()
                || (!c.thirdPerson() && !mc.options.getCameraType().isFirstPerson()));
    }
}
