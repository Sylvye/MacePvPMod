package dev.macepvpmod;

import java.io.IOException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AttributeSwapSettingsScreen extends Screen {
    private final Screen parent;
    private boolean visual, sound;
    private String soundId, error = "";
    private Button save, preview;
    public AttributeSwapSettingsScreen(Screen parent) {
        super(Component.literal("Attribute Swaps")); this.parent = parent;
        read(MacePvPMod.ATTRIBUTE_SWAP_CONFIG.current());
    }
    private void read(AttributeSwapConfig c) {
        visual = c.visualEnabled(); sound = c.soundEnabled(); soundId = c.soundId();
    }
    @Override protected void init() {
        int left = width / 2 - 150;
        addRenderableWidget(Button.builder(Component.literal("HUD text: " + (visual ? "On" : "Off")), b -> {
            visual = !visual; b.setMessage(Component.literal("HUD text: " + (visual ? "On" : "Off")));
        }).bounds(left, 45, 146, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Sound: " + (sound ? "On" : "Off")), b -> {
            sound = !sound; b.setMessage(Component.literal("Sound: " + (sound ? "On" : "Off")));
        }).bounds(left + 154, 45, 146, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Choose sound: " + SoundPlaylistScreen.name(soundId)), b ->
                minecraft.gui.setScreen(new SoundSelectorScreen(this, soundId, value -> soundId = value)))
                .bounds(left, 90, 300, 20).build());
        preview = addRenderableWidget(Button.builder(Component.literal("Test sound"), b -> AttributeSwaps.playSound(soundId))
                .bounds(left, 120, 146, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset defaults"), b -> {
            read(AttributeSwapConfig.defaults()); error = ""; rebuildWidgets();
        }).bounds(left + 154, 120, 146, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Edit in HUD"), b -> minecraft.gui.setScreen(new HudSettingsScreen(this, 5)))
                .bounds(left, 150, 300, 20).build());
        save = addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            try {
                MacePvPMod.ATTRIBUTE_SWAP_CONFIG.save(new AttributeSwapConfig(1, visual, sound,
                        net.minecraft.resources.Identifier.parse(soundId).toString()));
                onClose();
            } catch (IOException e) { error = "Could not save settings."; }
        }).bounds(left, height - 28, 146, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(left + 154, height - 28, 146, 20).build());
        updateValidity();
    }
    private void updateValidity() {
        boolean valid = AttributeSwaps.validSound(soundId);
        if (save != null) save.active = valid;
        if (preview != null) preview.active = valid;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float dt) {
        super.extractRenderState(g, x, y, dt);
        g.centeredText(font, title, width / 2, 16, 0xffffffff);
        g.text(font, "Minecraft sound ID", width / 2 - 150, 77, 0xffeeeeee);
        String status = AttributeSwaps.validSound(soundId) ? error : "Enter a valid Minecraft sound ID.";
        if (!status.isEmpty()) g.centeredText(font, status, width / 2, height - 42, 0xffff8888);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
