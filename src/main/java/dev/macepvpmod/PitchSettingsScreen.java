package dev.macepvpmod;

import java.io.IOException;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PitchSettingsScreen extends Screen {
    private final Screen parent;
    private boolean enabled, thirdPerson, advanced;
    private int barWidth, thickness, color, maxDisplacement;
    private double opacity, target, sensitivity, previewPitch = 40;
    private String colorText;
    private boolean colorValid = true;
    private String error = "";
    private Button save;
    private int left, columnWidth, previewTop;

    public PitchSettingsScreen(Screen parent) {
        super(Component.literal("MacePvPMod • Elytra Pitch Bar"));
        this.parent = parent;
        read(MacePvPMod.CONFIG.current());
    }
    private void read(PitchConfig c) {
        enabled = c.enabled(); barWidth = c.width(); thickness = c.thickness(); color = c.color();
        opacity = c.opacity(); target = c.targetPitch(); sensitivity = c.sensitivity();
        maxDisplacement = c.maxDisplacement(); thirdPerson = c.thirdPerson();
        colorText = String.format(Locale.ROOT, "%06X", color); colorValid = true;
    }
    private PitchConfig draft() {
        return new PitchConfig(1, enabled, barWidth, thickness, color, opacity, target,
                sensitivity, maxDisplacement, thirdPerson).validated();
    }
    @Override protected void init() {
        int panelWidth = Math.min(420, width - 20);
        left = (width - panelWidth) / 2;
        columnWidth = (panelWidth - 8) / 2;
        int right = left + columnWidth + 8;
        addRenderableWidget(Button.builder(Component.literal(advanced ? "Basic settings" : "Advanced settings"), b -> {
            advanced = !advanced; rebuildWidgets();
        }).bounds(left, 30, panelWidth, 20).build());
        if (!advanced) {
            toggle("Enabled", enabled, left, 56, v -> enabled = v, "Show the pitch guide while gliding.");
            slider("Width", barWidth, 10, 400, 1, right, 56, v -> barWidth = (int)v, "Line width in GUI pixels.");
            slider("Thickness", thickness, 1, 8, 1, left, 82, v -> thickness = (int)v, "Line thickness in GUI pixels.");
            slider("Opacity %", opacity * 100, 5, 100, 1, right, 82, v -> opacity = v / 100, "Transparency of the pitch line.");
            EditBox hex = new EditBox(font, left + 40, 108, columnWidth - 40, 20, Component.literal("Line color (hex RGB)"));
            hex.setMaxLength(6);
            hex.setValue(colorText);
            hex.setTooltip(Tooltip.create(Component.literal("Color: six hexadecimal RGB digits, e.g. 999999 for grey.")));
            hex.setResponder(value -> {
                colorText = value;
                colorValid = value.matches("[0-9a-fA-F]{6}");
                if (colorValid) color = Integer.parseInt(value, 16);
                hex.setTextColor(colorValid ? 0xffeeeeee : 0xffff7777);
                if (save != null) save.active = colorValid;
            });
            addRenderableWidget(hex);
            addRenderableWidget(Button.builder(Component.literal("Reset defaults"), b -> {
                read(PitchConfig.defaults()); error = ""; rebuildWidgets();
            }).bounds(right, 108, columnWidth, 20).tooltip(Tooltip.create(Component.literal("Reset all settings. Save to apply; Cancel to discard."))).build());
        } else {
            slider("Target °", target, -90, 90, .5, left, 56, v -> target = v, "Positive pitch looks down. Default: 40°. This is a guide, not a damage prediction.");
            slider("Pixels / degree", sensitivity, .1, 10, .1, right, 56, v -> sensitivity = v, "How far the line moves for each degree of difference.");
            slider("Max travel", maxDisplacement, 0, 200, 1, left, 82, v -> maxDisplacement = (int)v, "Maximum vertical movement in GUI pixels.");
            toggle("Third person", thirdPerson, right, 82, v -> thirdPerson = v, "Also show the guide while gliding in third person.");
        }
        previewTop = 138;
        slider("Preview pitch °", previewPitch, -90, 90, .5, left, previewTop, v -> previewPitch = v,
                "Simulated pitch for this preview only. Aim toward the grey line in game.");
        int buttonWidth = (panelWidth - 8) / 2;
        save = addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            try { MacePvPMod.CONFIG.save(draft()); onClose(); }
            catch (IOException ex) { error = "Couldn't save. Check config folder permissions."; }
        }).bounds(left, height - 28, buttonWidth, 20).build());
        save.active = colorValid;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(right, height - 28, buttonWidth, 20).build());
    }
    private void toggle(String label, boolean initial, int x, int y, Consumer<Boolean> setter, String hint) {
        addRenderableWidget(Button.builder(Component.literal(label + ": " + (initial ? "On" : "Off")), new Button.OnPress() {
            private boolean value = initial;
            @Override public void onPress(Button button) {
                value = !value; setter.accept(value);
                button.setMessage(Component.literal(label + ": " + (value ? "On" : "Off")));
            }
        }).bounds(x, y, columnWidth, 20).tooltip(Tooltip.create(Component.literal(hint))).build());
    }
    private void slider(String label, double initial, double min, double max, double step,
                        int x, int y, DoubleConsumer setter, String hint) {
        addRenderableWidget(new AbstractSliderButton(x, y, columnWidth, 20, Component.empty(), (initial - min) / (max - min)) {
            { updateMessage(); setTooltip(Tooltip.create(Component.literal(hint))); }
            private double actual() { return Math.max(min, Math.min(max, Math.round((min + value * (max - min)) / step) * step)); }
            @Override protected void updateMessage() {
                setMessage(Component.literal(label + ": " + String.format(Locale.ROOT, step >= 1 ? "%.0f" : "%.1f", actual())));
            }
            @Override protected void applyValue() { setter.accept(actual()); }
        });
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, title, width / 2, 12, 0xffffffff);
        if (!advanced) g.text(font, "Color #", left, 114, 0xffeeeeee);
        int top = previewTop + 25, bottom = height - 46;
        if (bottom > top + 12) {
            g.fill(left, top, width - left, bottom, 0xff25292d);
            int centerY = (top + bottom) / 2;
            PitchHud.draw(g, draft(), width / 2, centerY, previewPitch, width - 2 * left, bottom - top);
            g.fill(width / 2 - 3, centerY, width / 2 + 4, centerY + 1, 0xffeeeeee);
            g.fill(width / 2, centerY - 3, width / 2 + 1, centerY + 4, 0xffeeeeee);
        }
        String status = !colorValid ? "Enter a six-digit hex color." : error;
        if (!status.isEmpty()) g.centeredText(font, status, width / 2, height - 41, 0xffff8888);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
