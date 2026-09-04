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
    private final String[] texts = new String[4];
    private final int[] colors = new int[4];
    private double health, saturation, harpVolume, bassVolume, harpPitch, bassPitch, startInterval, endInterval;
    private int selected, state = 1, left, column;
    private java.util.List<SurvivalItemRule> healingItems, saturationItems;
    private java.util.List<SoundEntry> sounds;
    private Button save;
    private String error = "";

    public SurvivalSettingsScreen(Screen parent) {
        super(Component.literal("Survival instincts")); this.parent = parent;
        read(MacePvPMod.SURVIVAL_CONFIG.current());
    }
    private void read(SurvivalConfig c) {
        sounds = c.sounds();
        healingItems = c.healingItems(); saturationItems = c.saturationItems();
        enabled[0] = c.retotemEnabled(); enabled[1] = c.healingEnabled();
        sizes[0] = c.retotemSize(); sizes[1] = c.healingSize();
        x[0] = c.retotemX(); x[1] = c.healingX(); y[0] = c.retotemY(); y[1] = c.healingY();
        texts[0] = c.retotemText(); texts[1] = c.healthText(); texts[2] = c.saturationText(); texts[3] = c.combinedText();
        colors[0] = c.retotemColor(); colors[1] = c.healthColor(); colors[2] = c.saturationColor(); colors[3] = c.combinedColor();
        health = c.healthPercent(); saturation = c.saturationThreshold(); harpVolume = c.harpVolume(); bassVolume = c.bassVolume();
        harpPitch = c.harpPitch(); bassPitch = c.bassPitch();
        startInterval = c.audioStartInterval(); endInterval = c.audioEndInterval();
    }
    private boolean valid() {
        for (int i = 0; i < 4; i++) if (texts[i].isBlank()) return false;
        return endInterval <= startInterval;
    }
    private SurvivalConfig draft() {
        return new SurvivalConfig(1, enabled[0], enabled[1], texts[0], colors[0], sizes[0], x[0], y[0],
                texts[1], colors[1], texts[2], colors[2], texts[3], colors[3], sizes[1], x[1], y[1], health, saturation, harpVolume, bassVolume, harpPitch, bassPitch, startInterval, endInterval, healingItems, saturationItems, sounds).validated();
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
        general.addChild(Button.builder(Component.literal("Edit in HUD"), b -> minecraft.gui.setScreen(new HudSettingsScreen(this, selected == 0 ? 3 : 4))).bounds(0, 0, column, 20).build());
        content.addChild(general);
        if (i == 1) {
            var thresholds = LinearLayout.horizontal().spacing(8);
            thresholds.addChild(makeSlider("Health %", health, 0, 100, 1, 0, 0, column, v -> health = v));
            thresholds.addChild(makeSlider("Saturation /20", saturation, 0, 20, .5, 0, 0, column, v -> saturation = v));
            content.addChild(thresholds);
            itemSection(content, true, panel);
            itemSection(content, false, panel);
            content.addChild(new StringWidget(Component.literal("Low health audio"), font));
            content.addChild(Button.builder(Component.literal("Edit sound playlist (" + sounds.size() + ")"), b ->
                    minecraft.gui.setScreen(new SoundPlaylistScreen(this, sounds, result -> sounds = result)))
                    .bounds(0, 0, panel, 20).build());
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
        actions.addChild(Button.builder(Component.literal("Preview / place HUD"), b -> minecraft.gui.setScreen(new HudSettingsScreen(this, selected == 0 ? 3 : 4)))
                .bounds(0, 0, column, 20).build());
        content.addChild(actions);
        var scroll = new ScrollableLayout(minecraft, content, Math.max(40, height - 104));
        scroll.setMinWidth(panel);
        scroll.arrangeElements(); scroll.setX((width - scroll.getWidth()) / 2); scroll.setY(56);
        scroll.visitWidgets(this::addRenderableWidget);
        save = addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            try { MacePvPMod.SURVIVAL_CONFIG.save(draft()); onClose(); }
            catch (IOException e) { error = "Could not save settings."; }
        }).bounds(left, height - 28, column, 20).build());
        save.active = valid();
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(right, height - 28, column, 20).build());
    }
    private void itemSection(LinearLayout content, boolean healing, int panel) {
        var items = healing ? healingItems : saturationItems;
        String label = healing ? "Healing items" : "Saturation items";
        content.addChild(new StringWidget(Component.literal(label + " (" + items.size() + ")"), font));
        var row = LinearLayout.horizontal().spacing(4);
        int shown = Math.min(items.size(), Math.max(1, (panel - 112) / 40));
        Runnable edit = () -> minecraft.gui.setScreen(new SurvivalItemEditor(this, label, items,
                healing ? SurvivalItemRule.healingDefaults() : SurvivalItemRule.saturationDefaults(), result -> {
                    if (healing) healingItems = result; else saturationItems = result;
                }));
        for (int i = 0; i < shown; i++) row.addChild(new SurvivalItemButton(items.get(i), () -> true, edit));
        row.addChild(Button.builder(Component.literal("Edit list"), b -> edit.run()).bounds(0, 0, 100, 32).build());
        content.addChild(row);
        if (items.isEmpty()) content.addChild(new StringWidget(Component.literal("No items selected"), font));
    }
    private void warningSection(LinearLayout content, int warning, String label, int panel) {
        var section = LinearLayout.vertical().spacing(4);
        section.addChild(new StringWidget(Component.literal(label), font));
        var fields = LinearLayout.horizontal().spacing(8);
        int textWidth = panel;
        var textColumn = LinearLayout.vertical().spacing(3);
        textColumn.addChild(new StringWidget(Component.literal("Text"), font));
        EditBox text = new EditBox(font, 0, 0, textWidth, 20, Component.literal(label + " text"));
        text.setMaxLength(80); text.setValue(texts[warning]);
        text.setResponder(value -> { texts[warning] = value; if (save != null) save.active = valid(); });
        textColumn.addChild(text); fields.addChild(textColumn);
        section.addChild(fields); content.addChild(section);
    }
    private AbstractSliderButton makeSlider(String label, double initial, double min, double max, double step,
                                           int px, int py, int w, DoubleConsumer setter) {
        var slider = SettingsControls.slider(label, initial, min, max, step, w, setter);
        slider.setPosition(px, py);
        return slider;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        g.centeredText(font, title, width / 2, 12, 0xffffffff);
        String status = endInterval > startInterval ? "Critical gap must not exceed threshold gap."
                : valid() ? error : "Enter nonempty warning text.";
        if (!status.isEmpty()) g.centeredText(font, status, width / 2, height - 40, 0xffff8888);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
