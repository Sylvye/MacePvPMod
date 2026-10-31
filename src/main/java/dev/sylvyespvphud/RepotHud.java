package dev.sylvyespvphud;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

public final class RepotHud {
    private static final RepotState STATE = new RepotState();
    private static Object player, level;
    private static RepotConfig lastConfig;
    private static List<String> warnings = List.of();
    private static double pulsePhase;
    private RepotHud() {}

    static void reset() { STATE.clear(); warnings = List.of(); player = level = null; lastConfig = null; pulsePhase = 0; }

    static void tick(Minecraft mc) {
        var config = prepare(mc);
        if (config == null) return;
        Map<String, RepotState.Effect> active = new HashMap<>();
        for (var effect : mc.player.getActiveEffects()) {
            if (effect.getEffect().value().isInstantaneous()) continue;
            active.put(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString(),
                    snapshot(effect));
        }
        if (!mc.isPaused()) {
            warnings = STATE.tick(config, active, potions(mc.player));
            double period = period(config, STATE.details());
            pulsePhase = (pulsePhase + 1 / (period * 20)) % 1;
        }
    }

    private static RepotConfig prepare(Minecraft mc) {
        var survival = SylvyesPvPHud.SURVIVAL_CONFIG.current(); var config = survival.repot();
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || mc.player.isSpectator()
                || !survival.enabled() || !config.enabled()) { reset(); return null; }
        if (player != mc.player || level != mc.level || !config.equals(lastConfig)) reset();
        player = mc.player; level = mc.level; lastConfig = config;
        return config;
    }

    private static RepotState.Effect snapshot(MobEffectInstance effect) {
        return new RepotState.Effect(effect.getDuration(), effect.isAmbient(), effect.isInfiniteDuration());
    }

    public static void removingEffect(ClientboundRemoveMobEffectPacket packet) {
        var mc = Minecraft.getInstance();
        if (!mc.isSameThread() || prepare(mc) == null || packet.getEntity(mc.level) != mc.player) return;
        var effect = mc.player.getEffect(packet.effect());
        if (effect != null && !effect.getEffect().value().isInstantaneous())
            STATE.removed(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString(), snapshot(effect));
    }

    public static void entityEvent(Entity entity, byte event) {
        var mc = Minecraft.getInstance();
        if (event == 35 && entity == mc.player && prepare(mc) != null) STATE.totemPopped();
    }

    static List<RepotState.Warning> details() { return STATE.details(); }

    static double period(RepotConfig config, List<RepotState.Warning> effects) {
        int remaining = effects.stream().mapToInt(RepotState.Warning::remainingTicks).min().orElse(config.thresholdSeconds() * 20);
        return config.attention().period(remaining, config.thresholdSeconds());
    }

    static Set<String> potions(Player player) {
        Set<String> found = new HashSet<>();
        for (var stack : SurvivalItems.carried(player)) {
            if (stack.isEmpty() || !(stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION))) continue;
            var contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null) continue;
            for (var effect : contents.getAllEffects()) {
                if (!effect.isAmbient() && !effect.getEffect().value().isInstantaneous())
                    found.add(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString());
            }
        }
        return found;
    }

    static List<String> warnings() { return warnings; }

    static String name(String id) {
        var effect = BuiltInRegistries.MOB_EFFECT.getValue(Identifier.parse(id));
        return effect == null ? id : effect.getDisplayName().getString();
    }

    static void extract(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) {
        if (warnings.isEmpty()) return;
        var config = SylvyesPvPHud.SURVIVAL_CONFIG.current().repot();
        renderWarnings(g, config, STATE.details(), SylvyesPvPHud.HUD_CONFIG.current().repot(),
                g.guiWidth(), g.guiHeight(), false, pulsePhase + delta.getGameTimeDeltaPartialTick(false) / (period(config, STATE.details()) * 20));
    }

    // Compatibility for older preview callers: represent effects near the threshold.
    static HudRenderer.Bounds render(GuiGraphicsExtractor g, RepotConfig config, List<String> effects,
                                     HudStyle style, int width, int height, boolean muted) {
        return preview(g, config, effects, style, width, height, muted, 0);
    }

    static HudRenderer.Bounds preview(GuiGraphicsExtractor g, RepotConfig config, List<String> effects,
                                     HudStyle style, int width, int height, boolean muted, int state) {
        var sample = new ArrayList<RepotState.Warning>();
        for (int i = 0; i < effects.size(); i++) {
            boolean ended = state == 1 || state == 2 && i % 2 == 1;
            sample.add(new RepotState.Warning(effects.get(i), ended ? 0 : config.thresholdSeconds() * 20 / 2, ended));
        }
        double phase = (net.minecraft.util.Util.getMillis() / 1000.0 / period(config, sample)) % 1;
        return renderWarnings(g, config, sample, style, width, height, muted, phase);
    }

    static Component message(RepotConfig config, List<RepotState.Warning> effects, int normal) {
        var names = Component.empty();
        for (int i = 0; i < effects.size(); i++) {
            if (i > 0) names.append(Component.literal(", ").withColor(normal));
            var warning = effects.get(i);
            names.append(Component.literal(name(warning.id())).withColor(config.attention().color(warning.ended(), normal)));
        }
        var text = Component.empty();
        String[] parts = config.template().split(java.util.regex.Pattern.quote("{effect}"), -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) text.append(names.copy());
            text.append(Component.literal(parts[i]).withColor(normal));
        }
        return text;
    }

    static HudRenderer.Bounds renderWarnings(GuiGraphicsExtractor g, RepotConfig config, List<RepotState.Warning> effects,
                                             HudStyle style, int width, int height, boolean muted, double phase) {
        var known = effects.stream().filter(w -> BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(w.id()))
                .filter(effect -> !effect.value().isInstantaneous()).isPresent()).toList();
        double opacity = style.opacity() * (muted ? .35 : config.attention().opacity(phase));
        var shown = new HudStyle(style.anchorX(), style.anchorY(), style.alignX(), style.alignY(), style.x(), style.y(),
                style.scale(), style.color(), style.secondaryColor(), style.combinedColor(), style.width(), style.thickness(), opacity);
        if (known.isEmpty()) return HudRenderer.textColor(g, "Repot", shown, muted ? 0x65717d : style.color(), width, height);
        if (!config.icons()) return HudRenderer.textColor(g, message(config, muted ? known.stream().map(w -> new RepotState.Warning(w.id(),w.remainingTicks(),false)).toList() : known, muted ? 0x65717d : style.color()),
                shown, style.color(), width, height);
        // One pixel of padding keeps the border inside the measured bounds without shrinking native icons.
        int rowWidth = known.size() * 18 + (known.size() - 1) * config.iconSpacing() + 2;
        float scale = (float)Math.min(style.scale(), Math.min(width / (double)rowWidth, height / 20.0));
        var bounds = HudRenderer.bounds(style, rowWidth * scale, 20 * scale, width, height);
        int alpha = (int)Math.round(opacity * 255);
        g.pose().pushMatrix();
        g.pose().translate((float)bounds.x(), (float)bounds.y()); g.pose().scale(scale, scale);
        for (int i = 0; i < known.size(); i++) {
            var warning = known.get(i);
            var effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(warning.id())).orElseThrow();
            int x = 1 + i * (18 + config.iconSpacing());
            if (warning.ended() && config.attention().endedColorEnabled()) {
                int color = (alpha << 24) | (muted ? 0x65717d : config.attention().endedColor());
                g.fill(x - 1, 0, x + 19, 1, color); g.fill(x - 1, 19, x + 19, 20, color);
                g.fill(x - 1, 1, x, 19, color); g.fill(x + 18, 1, x + 19, 19, color);
            }
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effect), x, 1, 18, 18, (alpha << 24) | 0xffffff);
        }
        g.pose().popMatrix();
        return bounds;
    }
}
