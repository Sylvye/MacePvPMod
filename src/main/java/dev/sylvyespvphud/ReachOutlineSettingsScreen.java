package dev.sylvyespvphud;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Retained shortcut to the shared, profile-aware Reach Outlines editor. */
public final class ReachOutlineSettingsScreen extends Screen {
    private final Screen parent;
    public ReachOutlineSettingsScreen(Screen parent) {
        super(Component.literal("Reach Outlines"));
        this.parent = parent;
    }
    @Override protected void init() { minecraft.gui.setScreen(new SettingsScreen(parent,5)); }
}
