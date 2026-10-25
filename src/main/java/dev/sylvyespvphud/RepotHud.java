package dev.sylvyespvphud;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

final class RepotHud {
    private static final RepotState STATE = new RepotState();
    private static Object player, level;
    private static RepotConfig lastConfig;
    private static List<String> warnings = List.of();
    private RepotHud() {}

    static void reset() { STATE.clear(); warnings = List.of(); player = level = null; lastConfig = null; }

    static void tick(Minecraft mc) {
        var survival = SylvyesPvPHud.SURVIVAL_CONFIG.current();
        var config = survival.repot();
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || mc.player.isSpectator()
                || !survival.enabled() || !config.enabled()) { reset(); return; }
        if (player != mc.player || level != mc.level || !config.equals(lastConfig)) reset();
        player = mc.player; level = mc.level; lastConfig = config;
        Map<String, RepotState.Effect> active = new HashMap<>();
        for (var effect : mc.player.getActiveEffects()) {
            if (effect.getEffect().value().isInstantaneous()) continue;
            active.put(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString(),
                    new RepotState.Effect(effect.getDuration(), effect.isAmbient(), effect.isInfiniteDuration()));
        }
        if (!mc.isPaused()) warnings = STATE.tick(config, active, potions(mc.player));
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

    static void extract(GuiGraphicsExtractor g) {
        if (!warnings.isEmpty()) render(g, SylvyesPvPHud.SURVIVAL_CONFIG.current().repot(),
                warnings, SylvyesPvPHud.HUD_CONFIG.current().repot(), g.guiWidth(), g.guiHeight(), false);
    }

    static HudRenderer.Bounds render(GuiGraphicsExtractor g, RepotConfig config, List<String> effects,
                                     HudStyle style, int width, int height, boolean muted) {
        var known = effects.stream().filter(id -> BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(id))
                .filter(effect -> !effect.value().isInstantaneous()).isPresent()).toList();
        if (known.isEmpty()) return HudRenderer.textColor(g, "Repot", style, muted ? 0x65717d : style.color(), width, height);
        if (!config.icons()) return HudRenderer.textColor(g, config.format(known.stream().map(RepotHud::name).toList()),
                style, muted ? 0x65717d : style.color(), width, height);
        int rowWidth = known.size() * 18 + (known.size() - 1) * config.iconSpacing();
        float scale = (float)Math.min(style.scale(), Math.min(width / (double)rowWidth, height / 18.0));
        var bounds = HudRenderer.bounds(style, rowWidth * scale, 18 * scale, width, height);
        int alpha = (int)Math.round(style.opacity() * (muted ? .35 : 1) * 255);
        g.pose().pushMatrix();
        g.pose().translate((float)bounds.x(), (float)bounds.y()); g.pose().scale(scale, scale);
        for (int i = 0; i < known.size(); i++) {
            var effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(known.get(i))).orElseThrow();
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effect),
                    i * (18 + config.iconSpacing()), 0, 18, 18, (alpha << 24) | 0xffffff);
        }
        g.pose().popMatrix();
        return bounds;
    }
}
