package dev.sylvyespvphud;

import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compact profile chooser and manager opened by /hudprofile and Settings. */
public final class ProfileScreen extends Screen {
    private final Screen parent;
    private String selectedId;
    private String error = "";
    private int profilePage;
    private EditBox name;
    private EditBox server;

    public ProfileScreen(Screen parent) { super(Component.literal("HUD Profiles")); this.parent = parent; }

    @Override protected void init() {
        var profiles = SylvyesPvPHud.PROFILES.profiles();
        if (selectedId == null || profiles.stream().noneMatch(p -> p.id().equals(selectedId))) selectedId = SylvyesPvPHud.PROFILES.active().id();
        SettingsProfile selected = selected();
        int left = Math.max(12, width / 2 - 228), split = left + 146, right = split + 12;
        int capacity = Math.max(1, (height - 188) / 22);
        int selectedIndex = 0; for (int i = 0; i < profiles.size(); i++) if (profiles.get(i).id().equals(selectedId)) selectedIndex = i;
        profilePage = Math.min(selectedIndex / capacity, Math.max(0, (profiles.size() - 1) / capacity));
        int y = 50, start = profilePage * capacity, end = Math.min(profiles.size(), start + capacity);
        for (SettingsProfile profile : profiles.subList(start, end)) {
            String prefix = profile.id().equals(SylvyesPvPHud.PROFILES.active().id()) ? "● " : profile.id().equals(selectedId) ? "› " : "";
            addRenderableWidget(Button.builder(Component.literal(prefix + profile.name()), b -> { selectedId = profile.id(); rebuildWidgets(); })
                    .bounds(left, y, 138, 20).build());
            y += 22;
        }
        if (profiles.size() > capacity) {
            Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), b -> { selectedId = profiles.get(Math.max(0, start - capacity)).id(); rebuildWidgets(); }).bounds(left, height - 80, 34, 20).build());
            previous.active = start > 0;
            Button next = addRenderableWidget(Button.builder(Component.literal("›"), b -> { selectedId = profiles.get(Math.min(profiles.size() - 1, end)).id(); rebuildWidgets(); }).bounds(left + 104, height - 80, 34, 20).build());
            next.active = end < profiles.size();
        }
        y = 50;
        name = addRenderableWidget(new EditBox(font, right, y, 190, 20, Component.literal("Profile name")));
        name.setMaxLength(32); name.setValue(selected.name());
        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> run(() -> SylvyesPvPHud.PROFILES.rename(selectedId, name.getValue())))
                .bounds(right + 196, y, 68, 20).build());
        y += 28;
        addRenderableWidget(Button.builder(Component.literal("Use profile"), b -> run(() -> SylvyesPvPHud.PROFILES.switchManual(selectedId)))
                .bounds(right, y, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Duplicate"), b -> runSelect(() -> SylvyesPvPHud.PROFILES.duplicate(selectedId)))
                .bounds(right + 94, y, 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Share"), b -> minecraft.gui.setScreen(new ProfileShareScreen(this,selectedId))).bounds(right + 182, y, 82, 20).build());
        y += 28;
        server = addRenderableWidget(new EditBox(font, right, y, 190, 20, Component.literal("Server address")));
        server.setMaxLength(255); server.setHint(Component.literal("example.net[:port]"));
        addRenderableWidget(Button.builder(Component.literal("Add server"), b -> addServer(server.getValue()))
                .bounds(right + 196, y, 68, 20).build());
        y += 24;
        var current = minecraft.getCurrentServer();
        Button addCurrent = addRenderableWidget(Button.builder(Component.literal("Add current server"), b -> addServer(current.ip))
                .bounds(right, y, 140, 20).build());
        addCurrent.active = !minecraft.isLocalServer() && current != null;
        y += 28;
        for (String address : selected.servers()) {
            addRenderableWidget(Button.builder(Component.literal("× " + address), b -> run(() -> SylvyesPvPHud.PROFILES.removeServer(selectedId, address)))
                    .bounds(right, y, 264, 20).build());
            y += 22;
            if (y > height - 88) break;
        }
        int bottom = height - 52;
        addRenderableWidget(Button.builder(Component.literal("New defaults"), b -> runSelect(() -> SylvyesPvPHud.PROFILES.create("New Profile", false)))
                .bounds(left, bottom, 108, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Import clipboard"), b -> runSelect(() -> SylvyesPvPHud.PROFILES.importProfile(minecraft.keyboardHandler.getClipboard())))
                .bounds(left + 114, bottom, 116, 20).build());
        Button delete = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> confirmDelete()).bounds(left + 236, bottom, 74, 20).build());
        delete.active = profiles.size() > 1;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width - 92, height - 28, 80, 20).build());
    }

    private SettingsProfile selected() { return SylvyesPvPHud.PROFILES.profiles().stream().filter(p -> p.id().equals(selectedId)).findFirst().orElse(SylvyesPvPHud.PROFILES.active()); }
    private void addServer(String address) {
        try {
            SettingsProfile owner = SylvyesPvPHud.PROFILES.ownerOfServer(address);
            if (owner != null && !owner.id().equals(selectedId)) {
                minecraft.gui.setScreen(new SettingsConfirmScreen(this, "Move server assignment?",
                        "This server is assigned to " + owner.name() + ". Move it here?",
                        () -> { run(() -> SylvyesPvPHud.PROFILES.assignServer(selectedId, address)); minecraft.gui.setScreen(this); },
                        () -> minecraft.gui.setScreen(this)));
            } else run(() -> SylvyesPvPHud.PROFILES.assignServer(selectedId, address));
        } catch (Exception exception) { error = exception.getMessage(); }
    }
    private void confirmDelete() {
        String id = selectedId, label = selected().name();
        minecraft.gui.setScreen(new SettingsConfirmScreen(this, "Delete " + label + "?", "This cannot be undone.",
                () -> { run(() -> SylvyesPvPHud.PROFILES.delete(id)); minecraft.gui.setScreen(this); }, () -> minecraft.gui.setScreen(this)));
    }
    private void run(IoAction action) { try { action.run(); error = ""; rebuildWidgets(); } catch (Exception exception) { error = exception.getMessage(); } }
    private void runSelect(IoSupplier action) { try { selectedId = action.run().id(); error = ""; rebuildWidgets(); } catch (Exception exception) { error = exception.getMessage(); } }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
    @FunctionalInterface private interface IoSupplier { SettingsProfile run() throws IOException; }

    @Override public void onClose() {
        minecraft.gui.setScreen(parent instanceof SettingsScreen settings ? new SettingsScreen(settings.profileReturnParent()) : parent);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xff0e141b); g.fill(0, 0, width, 36, 0xff131b24);
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.text(font, "HUD PROFILES", 12, 14, 0xff62d8ff);
        g.text(font, "Active: " + SylvyesPvPHud.PROFILES.active().name(), Math.max(12, width / 2 - 228), 39, 0xffffffff);
        g.text(font, SylvyesPvPHud.PROFILES.selectionDescription(), Math.max(12, width / 2 - 70), 39, 0xff9ba8b5);
        if (!error.isEmpty()) g.text(font, error, 12, height - 76, error.contains("copied") ? 0xff77dd99 : 0xffff7777);
    }
}
