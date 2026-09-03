package dev.macepvpmod;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;

public final class DamageHud {
    private static LivingEntity target;
    private static float before;
    private static int pendingTicks, displayTicks;
    private static boolean confirmed, calculated;
    private static double calculatedAmount;
    private static String hit = "";
    private static Object level;
    private DamageHud() {}
    public static void attacked(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getMainHandItem().is(Items.MACE)
                || !MacePvPMod.DAMAGE_CONFIG.current().hitEnabled()) return;
        if (entity instanceof LivingEntity living) {
            calculated = MacePvPMod.DAMAGE_CONFIG.current().calculatedDamage();
            calculatedAmount = calculated ? MaceDamageCalculator.atAttack(mc.player) : 0;
            target = living; before = living.getHealth(); pendingTicks = 20; confirmed = false;
        }
    }
    public static void damageEvent(ClientboundDamageEventPacket packet) {
        var mc = Minecraft.getInstance();
        if (target != null && mc.player != null && packet.entityId() == target.getId()
                && packet.sourceCauseId() == mc.player.getId()) confirmed = true;
    }
    public static void tick(Minecraft mc) {
        if (mc.level != level || mc.player == null || !mc.player.isAlive()) {
            level = mc.level; target = null; displayTicks = 0; hit = ""; return;
        }
        if (displayTicks > 0) displayTicks--;
        if (!MacePvPMod.DAMAGE_CONFIG.current().hitEnabled()) { target = null; displayTicks = 0; return; }
        if (target == null) return;
        pendingTicks--;
        float damage = before - target.getHealth();
        if (confirmed && calculated) {
            show(String.format(Locale.ROOT, "%.1f damage (calc)", calculatedAmount)); target = null;
        } else if (confirmed && damage > 0) {
            show(String.format(Locale.ROOT, "%.1f damage", damage)); target = null;
        } else if (pendingTicks <= 0) {
            if (confirmed) show("Damage unavailable");
            target = null;
        }
    }
    private static void show(String text) { hit = text; displayTicks = MacePvPMod.DAMAGE_CONFIG.current().hitSeconds() * 20; }
    static String visibleHit() { return displayTicks > 0 ? hit : ""; }
    static boolean showFall(double distance) { return Double.isFinite(distance) && distance > 1.5; }
    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance(); var p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null || mc.gui.hud.isHidden()
                || !p.isAlive() || p.isSpectator()) return;
        var c = MacePvPMod.DAMAGE_CONFIG.current();
        if (c.fallEnabled() && !p.onGround() && showFall(p.fallDistance))
            draw(g, String.format(Locale.ROOT, "%.1f blocks", p.fallDistance), c.fallColor(), c.fallSize(), c.fallX(), c.fallY());
        if (c.hitEnabled() && displayTicks > 0) draw(g, hit, c.hitColor(), c.hitSize(), c.hitX(), c.hitY());
    }
    static void draw(GuiGraphicsExtractor g, String text, int color, double size, int x, int y) {
        var font = Minecraft.getInstance().font; float scale = (float)size;
        float textWidth = font.width(text) * scale;
        float px = Math.max(0, Math.min(g.guiWidth() - textWidth, g.guiWidth() / 2f + x - textWidth / 2));
        float py = Math.max(0, Math.min(g.guiHeight() - font.lineHeight * scale, g.guiHeight() / 2f + y));
        g.pose().pushMatrix();
        g.pose().translate(px, py); g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, 0xff000000 | color);
        g.pose().popMatrix();
    }
}
