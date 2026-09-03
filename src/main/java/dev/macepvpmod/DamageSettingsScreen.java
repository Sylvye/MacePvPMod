package dev.macepvpmod;

import java.io.IOException;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DamageSettingsScreen extends Screen {
    private final Screen parent;
    private final boolean[] enabled = new boolean[2];
    private final int[] colors = new int[2], x = new int[2], y = new int[2];
    private final double[] sizes = new double[2];
    private final String[] hex = new String[2];
    private boolean calculatedDamage;
    private int selected, seconds, left, column;
    private String error = "";
    private Button save;
    public DamageSettingsScreen(Screen parent) {
        super(Component.literal("Damage Counter")); this.parent = parent;
        read(MacePvPMod.DAMAGE_CONFIG.current());
    }
    private void read(DamageConfig c) {
        calculatedDamage = c.calculatedDamage();
        enabled[0] = c.fallEnabled(); enabled[1] = c.hitEnabled();
        colors[0] = c.fallColor(); colors[1] = c.hitColor();
        sizes[0] = c.fallSize(); sizes[1] = c.hitSize();
        x[0] = c.fallX(); x[1] = c.hitX(); y[0] = c.fallY(); y[1] = c.hitY(); seconds = c.hitSeconds();
        for (int i = 0; i < 2; i++) hex[i] = String.format(Locale.ROOT, "%06X", colors[i]);
    }
    private boolean valid() { return hex[0].matches("[0-9a-fA-F]{6}") && hex[1].matches("[0-9a-fA-F]{6}"); }
    private DamageConfig draft() { return new DamageConfig(1, enabled[0], colors[0], sizes[0], x[0], y[0], enabled[1], colors[1], sizes[1], x[1], y[1], seconds, calculatedDamage).validated(); }
    @Override protected void init() {
        int panel = Math.min(420, width - 20); left = (width - panel) / 2; column = (panel - 8) / 2;
        int right = left + column + 8, i = selected;
        for (int tab = 0; tab < 2; tab++) {
            final int index = tab;
            Button b = addRenderableWidget(Button.builder(Component.literal(tab == 0 ? "Fall distance" : "Mace hit damage"), button -> {
                selected = index; rebuildWidgets();
            }).bounds(tab == 0 ? left : right, 30, column, 20).build());
            b.active = selected != tab;
        }
        addRenderableWidget(Button.builder(Component.literal("Enabled: " + (enabled[i] ? "On" : "Off")), b -> {
            enabled[i] = !enabled[i]; b.setMessage(Component.literal("Enabled: " + (enabled[i] ? "On" : "Off")));
        }).bounds(left, 56, column, 20).build());
        slider("Size", sizes[i], .5, 4, .1, right, 56, v -> sizes[i] = v);
        slider("Horizontal", x[i], -width / 2, width / 2, 1, left, 82, v -> x[i] = (int)v);
        slider("Vertical", y[i], -height / 2, height / 2, 1, right, 82, v -> y[i] = (int)v);
        EditBox color = new EditBox(font, left + 42, 108, column - 42, 20, Component.literal("Color hex RGB"));
        color.setMaxLength(6); color.setValue(hex[i]);
        color.setTooltip(Tooltip.create(Component.literal("Six RGB hex digits, e.g. FF6666. Position is relative to the crosshair.")));
        color.setTextColor(hex[i].matches("[0-9a-fA-F]{6}") ? 0xffeeeeee : 0xffff7777);
        color.setResponder(value -> {
            hex[i] = value;
            boolean ok = value.matches("[0-9a-fA-F]{6}");
            if (ok) colors[i] = Integer.parseInt(value, 16);
            color.setTextColor(ok ? 0xffeeeeee : 0xffff7777);
            if (save != null) save.active = valid();
        });
        addRenderableWidget(color);
        if (i == 1) slider("Seconds", seconds, 1, 10, 1, right, 108, v -> seconds = (int)v);
        addRenderableWidget(Button.builder(Component.literal("Reset damage defaults"), b -> { read(DamageConfig.defaults()); error = ""; rebuildWidgets(); })
                .bounds(left, 134, column, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Preview position"), b -> minecraft.gui.setScreen(new Preview()))
                .bounds(right, 134, column, 20).build());
        if (i == 1) addRenderableWidget(Button.builder(Component.literal(modeLabel()), b -> {
            calculatedDamage = !calculatedDamage; b.setMessage(Component.literal(modeLabel()));
        }).bounds(left, 160, panel, 20).tooltip(Tooltip.create(Component.literal(
                "Reported: server health lost. Calculated: raw mace damage before defenses, including Density, cooldown and critical hits. Breach piercing is ignored."))).build());
        save = addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            try { MacePvPMod.DAMAGE_CONFIG.save(draft()); onClose(); }
            catch (IOException e) { error = "Could not save settings."; }
        }).bounds(left, height - 28, column, 20).build()); save.active = valid();
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(right, height - 28, column, 20).build());
    }
    private String modeLabel() { return "Damage: " + (calculatedDamage ? "Calculated" : "Reported"); }
    private void slider(String label, double initial, double min, double max, double step, int px, int py, DoubleConsumer setter) {
        addRenderableWidget(new AbstractSliderButton(px, py, column, 20, Component.empty(), Math.max(0, Math.min(1, (initial - min) / (max - min)))) {
            { updateMessage(); }
            private double actual() { return Math.round((min + value * (max - min)) / step) * step; }
            @Override protected void updateMessage() { setMessage(Component.literal(label + ": " + String.format(Locale.ROOT, step < 1 ? "%.1f" : "%.0f", actual()))); }
            @Override protected void applyValue() { setter.accept(actual()); }
        });
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        g.centeredText(font, title, width / 2, 12, 0xffffffff);
        g.text(font, "Color #", left, 114, 0xffeeeeee);
        String status = !valid() ? "Enter six hex digits in each color field." : error;
        if (!status.isEmpty()) g.centeredText(font, status, width / 2, height - 41, 0xffff8888);
    }
    private final class Preview extends Screen {
        Preview() { super(Component.literal("Damage Counter • Position Preview")); }
        @Override protected void init() {
            addRenderableWidget(Button.builder(Component.literal("Back to settings"), b -> onClose())
                    .bounds(width / 2 - 90, height - 28, 180, 20).build());
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            super.extractRenderState(g, mx, my, dt);
            g.centeredText(font, title, width / 2, 12, 0xffffffff);
            g.centeredText(font, Component.literal("Sample values • position relative to the crosshair"), width / 2, 28, 0xffbbbbbb);
            if (enabled[0]) DamageHud.draw(g, "12.5 blocks", colors[0], sizes[0], x[0], y[0]);
            if (enabled[1]) DamageHud.draw(g, calculatedDamage ? "18.0 damage (calc)" : "18.0 damage", colors[1], sizes[1], x[1], y[1]);
            g.fill(width / 2 - 3, height / 2, width / 2 + 4, height / 2 + 1, 0xffaaaaaa);
            g.fill(width / 2, height / 2 - 3, width / 2 + 1, height / 2 + 4, 0xffaaaaaa);
        }
        @Override public void onClose() { minecraft.gui.setScreen(DamageSettingsScreen.this); }
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
