package dev.macepvpmod;

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
    private static double calculatedAmount, attackBlocks;
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
            attackBlocks = mc.player.fallDistance;
            target = living; before = living.getHealth(); pendingTicks = 20; confirmed = false;
        }
    }
    public static void damageEvent(ClientboundDamageEventPacket packet) {
        var mc = Minecraft.getInstance();
        if (target != null && mc.player != null && packet.entityId() == target.getId()
                && packet.sourceCauseId() == mc.player.getId()) confirmed = true;
    }
    public static void tick(Minecraft mc) {
        FallCounter.tick(mc.player);
        if (mc.level != level || mc.player == null || !mc.player.isAlive()) {
            FallCounter.reset();
            level = mc.level; target = null; displayTicks = 0; hit = ""; return;
        }
        if (displayTicks > 0) displayTicks--;
        if (!MacePvPMod.DAMAGE_CONFIG.current().hitEnabled()) { target = null; displayTicks = 0; return; }
        if (target == null) return;
        pendingTicks--;
        float damage = before - target.getHealth();
        if (confirmed && calculated) {
            show(DamageText.format(MacePvPMod.DAMAGE_CONFIG.current().hitTemplate(), attackBlocks, calculatedAmount)); target = null;
        } else if (confirmed && damage > 0) {
            show(DamageText.format(MacePvPMod.DAMAGE_CONFIG.current().hitTemplate(), attackBlocks, damage)); target = null;
        } else if (pendingTicks <= 0) {
            if (confirmed) show("Damage unavailable");
            target = null;
        }
    }
    private static void show(String text) { hit = text; displayTicks = MacePvPMod.DAMAGE_CONFIG.current().hitSeconds() * 20; }
    static String visibleHit() { return displayTicks > 0 ? hit : ""; }
    static boolean showFall(double distance) { return showFall(distance, 1.5); }
    static boolean showFall(double distance, double threshold) {
        return Double.isFinite(distance) && Double.isFinite(threshold) && distance > threshold;
    }
    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance(); var p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null || mc.gui.hud.isHidden()
                || !p.isAlive() || p.isSpectator()) return;
        var c = MacePvPMod.DAMAGE_CONFIG.current();
        double fallBlocks = FallCounter.distance(p);
        if (c.fallEnabled() && showFall(fallBlocks, c.fallThreshold()))
            HudRenderer.text(g, DamageText.format(c.fallTemplate(), fallBlocks, 0), MacePvPMod.HUD_CONFIG.current().fall(), 0);
        if (c.hitEnabled() && displayTicks > 0) HudRenderer.text(g, hit, MacePvPMod.HUD_CONFIG.current().hit(), 0);
    }
}
