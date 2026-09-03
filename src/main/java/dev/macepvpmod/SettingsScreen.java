package dev.macepvpmod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Module directory: each module owns a separate settings screen. */
public final class SettingsScreen extends Screen {
    private final Screen parent;
    public SettingsScreen(Screen parent) { super(Component.literal("MacePvPMod • Modules")); this.parent = parent; }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Elytra Pitch Bar"), b -> minecraft.gui.setScreen(new PitchSettingsScreen(this)))
                .bounds(width / 2 - 110, 60, 220, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Damage Counter"), b -> minecraft.gui.setScreen(new DamageSettingsScreen(this)))
                .bounds(width / 2 - 110, 94, 220, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Attribute Swaps"), b -> minecraft.gui.setScreen(new AttributeSwapSettingsScreen(this)))
                .bounds(width / 2 - 110, 128, 220, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Survival instincts"), b -> minecraft.gui.setScreen(new SurvivalSettingsScreen(this)))
                .bounds(width / 2 - 110, 162, 220, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 110, height - 30, 220, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float dt) {
        super.extractRenderState(g, x, y, dt);
        g.centeredText(font, title, width / 2, 20, 0xffffffff);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
