package dev.sylvyespvphud;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

/** Isolated selector draft. Selection order is also the HUD order. */
final class RepotEffectScreen extends Screen {
    private final Screen parent;
    private final List<String> selected;
    private final Consumer<List<String>> apply;
    private final List<AbstractWidget> listWidgets = new ArrayList<>();
    private String query = "";
    private boolean selectedOnly;
    private int panel, left;

    RepotEffectScreen(Screen parent, List<String> selected, Consumer<List<String>> apply) {
        super(Component.literal("Repot effects")); this.parent = parent;
        this.selected = new ArrayList<>(selected); this.apply = apply;
    }

    @Override protected void init() {
        listWidgets.clear(); panel = Math.min(480, width - 32); left = (width - panel) / 2;
        var search = new EditBox(font, left, 30, panel, 20, Component.literal("Search effects"));
        search.setHint(Component.literal("Search effect names or IDs")); search.setValue(query);
        search.setResponder(value -> { query = value; refresh(); }); addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal(selectedOnly ? "Selected only" : "All effects"), b -> {
            selectedOnly = !selectedOnly; rebuildWidgets();
        }).bounds(left, 54, panel, 20).build());
        int w = (panel - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("Reset defaults"), b -> {
            selected.clear(); selected.addAll(RepotConfig.defaults().effects()); refresh();
        }).bounds(left, height - 28, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            apply.accept(List.copyOf(selected)); minecraft.gui.setScreen(parent);
        }).bounds(left + w + 4, height - 28, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(left + 2 * (w + 4), height - 28, w, 20).build());
        refresh();
    }

    private void refresh() {
        listWidgets.forEach(this::removeWidget); listWidgets.clear();
        var catalog = new ArrayList<String>();
        for (var effect : BuiltInRegistries.MOB_EFFECT)
            if (!effect.isInstantaneous()) catalog.add(BuiltInRegistries.MOB_EFFECT.getKey(effect).toString());
        for (String id : selected) if (!catalog.contains(id) && BuiltInRegistries.MOB_EFFECT.get(net.minecraft.resources.Identifier.parse(id)).isEmpty()) catalog.add(id);
        catalog.sort(Comparator.comparing(RepotHud::name, String.CASE_INSENSITIVE_ORDER));
        if (selectedOnly) { catalog.sort(Comparator.comparingInt(id -> { int i=selected.indexOf(id); return i<0?Integer.MAX_VALUE:i; })); catalog.removeIf(id -> !selected.contains(id)); }
        var rows = LinearLayout.vertical().spacing(4);
        int count = 0;
        for (String id : catalog) {
            if (!(RepotHud.name(id) + " " + id).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT).strip())) continue;
            count++;
            var row = LinearLayout.horizontal().spacing(4);
            row.addChild(Button.builder(Component.literal((selected.contains(id) ? "✓ " : "") + RepotHud.name(id)), b -> {
                if (!selected.remove(id)) selected.add(id); refresh();
            }).bounds(0, 0, panel - 64, 20).tooltip(Tooltip.create(Component.literal(id))).build());
            var up = row.addChild(Button.builder(Component.literal("↑"), b -> move(id, -1)).bounds(0, 0, 28, 20).build());
            var down = row.addChild(Button.builder(Component.literal("↓"), b -> move(id, 1)).bounds(0, 0, 28, 20).build());
            int index = selected.indexOf(id); up.active = index > 0; down.active = index >= 0 && index < selected.size() - 1;
            rows.addChild(row);
        }
        if (count == 0) rows.addChild(new StringWidget(Component.literal("No matching effects"), font));
        var scroll = new ScrollableLayout(minecraft, rows, Math.max(32, height - 132));
        scroll.setMinWidth(panel); scroll.arrangeElements(); scroll.setX(left); scroll.setY(80);
        scroll.visitWidgets(w -> { listWidgets.add(w); addRenderableWidget(w); });
    }

    private void move(String id, int offset) {
        int index = selected.indexOf(id); Collections.swap(selected, index, index + offset); refresh();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        g.centeredText(font, title, width / 2, 12, 0xffffffff);
        g.centeredText(font, selected.size() + " selected • Arrows change warning order", width / 2, height - 43, 0xffbbbbbb);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
