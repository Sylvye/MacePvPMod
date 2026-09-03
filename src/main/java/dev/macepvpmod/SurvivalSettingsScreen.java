package dev.macepvpmod;

import java.io.IOException;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.network.chat.Component;

public final class SurvivalSettingsScreen extends Screen {
    private final Screen parent;
    private final boolean[] enabled = new boolean[2];
    private final double[] sizes = new double[2], x = new double[2], y = new double[2];
    private final String[] texts = new String[4], hex = new String[4];
    private final int[] colors = new int[4];
    private double health, saturation, harpVolume, bassVolume, harpPitch, bassPitch, startInterval, endInterval;
    private int selected, state = 1, left, column;
    private Button save;
    private String error = "";

    public SurvivalSettingsScreen(Screen parent) {
        super(Component.literal("Survival instincts")); this.parent = parent;
        read(MacePvPMod.SURVIVAL_CONFIG.current());
    }
    private void read(SurvivalConfig c) {
        enabled[0] = c.retotemEnabled(); enabled[1] = c.healingEnabled();
        sizes[0] = c.retotemSize(); sizes[1] = c.healingSize();
        x[0] = c.retotemX(); x[1] = c.healingX(); y[0] = c.retotemY(); y[1] = c.healingY();
        texts[0] = c.retotemText(); texts[1] = c.healthText(); texts[2] = c.saturationText(); texts[3] = c.combinedText();
        colors[0] = c.retotemColor(); colors[1] = c.healthColor(); colors[2] = c.saturationColor(); colors[3] = c.combinedColor();
        for (int i = 0; i < 4; i++) hex[i] = String.format(Locale.ROOT, "%06X", colors[i]);
        health = c.healthPercent(); saturation = c.saturationThreshold(); harpVolume = c.harpVolume(); bassVolume = c.bassVolume();
        harpPitch = c.harpPitch(); bassPitch = c.bassPitch();
        startInterval = c.audioStartInterval(); endInterval = c.audioEndInterval();
    }
    private boolean valid() {
        for (int i = 0; i < 4; i++) if (!hex[i].matches("[0-9a-fA-F]{6}") || texts[i].isBlank()) return false;
        return endInterval <= startInterval;
    }
    private SurvivalConfig draft() {
        return new SurvivalConfig(1, enabled[0], enabled[1], texts[0], colors[0], sizes[0], x[0], y[0],
                texts[1], colors[1], texts[2], colors[2], texts[3], colors[3], sizes[1], x[1], y[1], health, saturation, harpVolume, bassVolume, harpPitch, bassPitch, startInterval, endInterval).validated();
    }
    private String stateLabel() { return state == 1 ? "Low health" : state == 2 ? "Low saturation" : "Health + saturation"; }
    @Override protected void init() {
        int panel = Math.min(440, width - 32); left = (width - panel) / 2; column = (panel - 8) / 2;
        int right = left + column + 8, i = selected;
        for (int tab = 0; tab < 2; tab++) {
            final int index = tab;
            Button b = addRenderableWidget(Button.builder(Component.literal(tab == 0 ? "Retotem" : "Healing"), button -> {
                selected = index; rebuildWidgets();
            }).bounds(tab == 0 ? left : right, 30, column, 20).build());
            b.active = selected != tab;
        }
        var content = LinearLayout.vertical().spacing(8);
        var general = LinearLayout.horizontal().spacing(8);
        general.addChild(Button.builder(Component.literal("Enabled: " + (enabled[i] ? "On" : "Off")), b -> {
            enabled[i] = !enabled[i]; b.setMessage(Component.literal("Enabled: " + (enabled[i] ? "On" : "Off")));
        }).bounds(0, 0, column, 20).build());
        general.addChild(makeSlider("Size", sizes[i], .5, 4, .1, 0, 0, column, v -> sizes[i] = v));
        content.addChild(general);
        var position = LinearLayout.horizontal().spacing(8);
        position.addChild(makeSlider("Horizontal %", x[i], 0, 100, 1, 0, 0, column, v -> x[i] = v));
        position.addChild(makeSlider("Vertical %", y[i], 0, 100, 1, 0, 0, column, v -> y[i] = v));
        content.addChild(position);
        if (i == 1) {
            var thresholds = LinearLayout.horizontal().spacing(8);
            thresholds.addChild(makeSlider("Health %", health, 0, 100, 1, 0, 0, column, v -> health = v));
            thresholds.addChild(makeSlider("Saturation /20", saturation, 0, 20, .5, 0, 0, column, v -> saturation = v));
            content.addChild(thresholds);
            content.addChild(new StringWidget(Component.literal("Low health audio"), font));
            var harp = LinearLayout.horizontal().spacing(8);
            var harpGain = makeSlider("Harp volume %", harpVolume * 100, 0, 100, 1, 0, 0, column, v -> harpVolume = v / 100);
            harpGain.setTooltip(Tooltip.create(Component.literal("Lower HP makes notes louder and faster. This is the maximum volume. 0% mutes harp; master volume also applies.")));
            harp.addChild(harpGain);
            var harpTone = makeSlider("Harp pitch", harpPitch, .5, 2, .05, 0, 0, column, v -> harpPitch = v);
            harpTone.setTooltip(Tooltip.create(Component.literal("Base pitch; each note varies randomly by up to 5%.")));
            harp.addChild(harpTone); content.addChild(harp);
            var bass = LinearLayout.horizontal().spacing(8);
            var bassGain = makeSlider("Bass volume %", bassVolume * 100, 0, 100, 1, 0, 0, column, v -> bassVolume = v / 100);
            bassGain.setTooltip(Tooltip.create(Component.literal("Lower HP makes notes louder and faster. This is the maximum volume. 0% mutes bass; master volume also applies.")));
            bass.addChild(bassGain);
            var bassTone = makeSlider("Bass pitch", bassPitch, .5, 2, .05, 0, 0, column, v -> bassPitch = v);
            bassTone.setTooltip(Tooltip.create(Component.literal("Base pitch; each note varies randomly by up to 5%.")));
            bass.addChild(bassTone); content.addChild(bass);
            var intervals = LinearLayout.horizontal().spacing(8);
            var start = makeSlider("Threshold gap (s)", startInterval, .05, 3, .05, 0, 0, column, v -> {
                startInterval = v; if (save != null) save.active = valid();
            });
            var end = makeSlider("Critical gap (s)", endInterval, .05, 3, .05, 0, 0, column, v -> {
                endInterval = v; if (save != null) save.active = valid();
            });
            start.setTooltip(Tooltip.create(Component.literal("Seconds between notes at the low-health threshold. Default: 0.75. Must be at least the critical gap.")));
            end.setTooltip(Tooltip.create(Component.literal("Seconds between notes as HP approaches zero. Default: 0.25. Smaller means faster.")));
            intervals.addChild(start); intervals.addChild(end); content.addChild(intervals);
            warningSection(content, 1, "Low health", panel);
            warningSection(content, 2, "Low saturation", panel);
            warningSection(content, 3, "Low health + saturation", panel);
        } else {
            warningSection(content, 0, "Retotem warning", panel);
        }
        var actions = LinearLayout.horizontal().spacing(8);
        actions.addChild(Button.builder(Component.literal("Reset defaults"), b -> {
            read(SurvivalConfig.defaults()); error = ""; rebuildWidgets();
        }).bounds(0, 0, column, 20).build());
        actions.addChild(Button.builder(Component.literal("Preview / place HUD"), b -> minecraft.gui.setScreen(new Preview()))
                .bounds(0, 0, column, 20).build());
        content.addChild(actions);
        var scroll = new ScrollableLayout(minecraft, content, Math.max(40, height - 104));
        scroll.setMinWidth(panel);
        scroll.arrangeElements(); scroll.setX(left); scroll.setY(56);
        scroll.visitWidgets(this::addRenderableWidget);
        save = addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            try { MacePvPMod.SURVIVAL_CONFIG.save(draft()); onClose(); }
            catch (IOException e) { error = "Could not save settings."; }
        }).bounds(left, height - 28, column, 20).build());
        save.active = valid();
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(right, height - 28, column, 20).build());
    }
    private void warningSection(LinearLayout content, int warning, String label, int panel) {
        var section = LinearLayout.vertical().spacing(4);
        section.addChild(new StringWidget(Component.literal(label), font));
        var fields = LinearLayout.horizontal().spacing(8);
        int colorWidth = 72, textWidth = panel - colorWidth - 8;
        var textColumn = LinearLayout.vertical().spacing(3);
        textColumn.addChild(new StringWidget(Component.literal("Text"), font));
        EditBox text = new EditBox(font, 0, 0, textWidth, 20, Component.literal(label + " text"));
        text.setMaxLength(80); text.setValue(texts[warning]);
        text.setResponder(value -> { texts[warning] = value; if (save != null) save.active = valid(); });
        textColumn.addChild(text); fields.addChild(textColumn);
        var colorColumn = LinearLayout.vertical().spacing(3);
        colorColumn.addChild(new StringWidget(Component.literal("Color #"), font));
        EditBox color = new EditBox(font, 0, 0, colorWidth, 20, Component.literal(label + " color hex RGB"));
        color.setMaxLength(6); color.setValue(hex[warning]);
        color.setTooltip(Tooltip.create(Component.literal("Six RGB hex digits, e.g. FF9900 for orange.")));
        color.setTextColor(hex[warning].matches("[0-9a-fA-F]{6}") ? 0xffeeeeee : 0xffff7777);
        color.setResponder(value -> {
            hex[warning] = value; boolean ok = value.matches("[0-9a-fA-F]{6}");
            if (ok) colors[warning] = Integer.parseInt(value, 16);
            color.setTextColor(ok ? 0xffeeeeee : 0xffff7777);
            if (save != null) save.active = valid();
        });
        colorColumn.addChild(color); fields.addChild(colorColumn);
        section.addChild(fields); content.addChild(section);
    }
    private AbstractSliderButton makeSlider(String label, double initial, double min, double max, double step,
                                           int px, int py, int w, DoubleConsumer setter) {
        return new AbstractSliderButton(px, py, w, 20, Component.empty(), (initial - min) / (max - min)) {
            { updateMessage(); }
            private double actual() { return Math.round((min + value * (max - min)) / step) * step; }
            @Override protected void updateMessage() { setMessage(Component.literal(label + ": " + String.format(Locale.ROOT, step < .1 ? "%.2f" : step < 1 ? "%.1f" : "%.0f", actual()))); }
            @Override protected void applyValue() { setter.accept(actual()); }
        };
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        g.centeredText(font, title, width / 2, 12, 0xffffffff);
        String status = endInterval > startInterval ? "Critical gap must not exceed threshold gap."
                : valid() ? error : "Use nonempty text and six-digit hex colors.";
        if (!status.isEmpty()) g.centeredText(font, status, width / 2, height - 40, 0xffff8888);
    }
    private final class Preview extends Screen {
        Preview() { super(Component.literal("Position preview")); }
        @Override protected void init() {
            int w = Math.min(200, (width - 30) / 2), l = width / 2 - w - 4, r = width / 2 + 4;
            addRenderableWidget(makeSlider("Horizontal %", x[selected], 0, 100, 1, l, height - 78, w, v -> x[selected] = v));
            addRenderableWidget(makeSlider("Vertical %", y[selected], 0, 100, 1, r, height - 78, w, v -> y[selected] = v));
            addRenderableWidget(makeSlider("Size", sizes[selected], .5, 4, .1, l, height - 54, w, v -> sizes[selected] = v));
            addRenderableWidget(Button.builder(Component.literal("Sample: " + stateLabel()), b -> {
                state = state % 3 + 1; b.setMessage(Component.literal("Sample: " + stateLabel()));
            }).bounds(r, height - 54, w, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Back to settings"), b -> onClose())
                    .bounds(width / 2 - 90, height - 28, 180, 20).build());
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            super.extractRenderState(g, mx, my, dt);
            // Steady samples make positioning possible even when the warning is disabled.
            var c = draft();
            SurvivalHud.draw(g, c.retotemText(), c.retotemColor(), c.retotemSize(), c.retotemX(), c.retotemY());
            SurvivalHud.drawHealing(g, c, state);
            g.centeredText(font, "Positioning " + (selected == 0 ? "Retotem" : "Healing") + " • steady samples", width / 2, height - 94, 0xffbbbbbb);
        }
        @Override public void onClose() { minecraft.gui.setScreen(SurvivalSettingsScreen.this); }
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
