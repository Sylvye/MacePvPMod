package dev.macepvpmod;

import java.io.IOException;
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
    private double opacity, target, sensitivity;
    private String error = "";
    private Button save;
    private int left, columnWidth;

    public PitchSettingsScreen(Screen parent) {
        super(Component.literal("MacePvPMod • Elytra Pitch Bar"));
        this.parent = parent;
        read(MacePvPMod.CONFIG.current());
    }
    private void read(PitchConfig c) {
        enabled = c.enabled(); barWidth = c.width(); thickness = c.thickness(); color = c.color();
        opacity = c.opacity(); target = c.targetPitch(); sensitivity = c.sensitivity();
        maxDisplacement = c.maxDisplacement(); thirdPerson = c.thirdPerson();
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
            addRenderableWidget(Button.builder(Component.literal("Edit in HUD"), b -> minecraft.gui.setScreen(new HudSettingsScreen(this, 0)))
                    .bounds(right, 56, columnWidth, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Reset behavior defaults"), b -> { read(PitchConfig.defaults()); rebuildWidgets(); })
                    .bounds(left, 82, columnWidth, 20).build());
        } else {
            slider("Target °", target, -90, 90, .5, left, 56, v -> target = v, "Positive pitch looks down. Default: 40°. This is a guide, not a damage prediction.");
            slider("Pixels / degree", sensitivity, .1, 10, .1, right, 56, v -> sensitivity = v, "How far the line moves for each degree of difference.");
            slider("Max travel", maxDisplacement, 0, 200, 1, left, 82, v -> maxDisplacement = (int)v, "Maximum vertical movement in GUI pixels.");
            toggle("Third person", thirdPerson, right, 82, v -> thirdPerson = v, "Also show the guide while gliding in third person.");
        }
        int buttonWidth = (panelWidth - 8) / 2;
        save = addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            try { MacePvPMod.CONFIG.save(draft()); onClose(); }
            catch (IOException ex) { error = "Couldn't save. Check config folder permissions."; }
        }).bounds(left, height - 28, buttonWidth, 20).build());
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
        var slider = SettingsControls.slider(label, initial, min, max, step, columnWidth, setter);
        slider.setPosition(x, y);
        slider.setTooltip(Tooltip.create(Component.literal(hint)));
        addRenderableWidget(slider);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, title, width / 2, 12, 0xffffffff);

        String status = error;
        if (!status.isEmpty()) g.centeredText(font, status, width / 2, height - 41, 0xffff8888);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
