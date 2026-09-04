package dev.macepvpmod;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SurvivalItemEditor extends Screen {
    private final Screen parent;
    private final LinkedHashSet<SurvivalItemRule> selected;
    private final List<SurvivalItemRule> defaults, catalog;
    private final Consumer<List<SurvivalItemRule>> apply;
    private final List<AbstractWidget> gridWidgets = new ArrayList<>();
    private String query = "";
    private int filter, left, panel;
    private EditBox search;
    SurvivalItemEditor(Screen parent, String title, List<SurvivalItemRule> selected,
                       List<SurvivalItemRule> defaults, Consumer<List<SurvivalItemRule>> apply) {
        super(Component.literal(title)); this.parent = parent;
        this.selected = new LinkedHashSet<>(selected); this.defaults = defaults; this.apply = apply;
        this.catalog = SurvivalItems.catalog();
        for (var rule : selected) if (!catalog.contains(rule)) catalog.add(rule);
        catalog.sort(Comparator.comparing(SurvivalItems::name, String.CASE_INSENSITIVE_ORDER));
    }
    @Override protected void init() {
        gridWidgets.clear();
        panel = Math.min(560, width - 32); left = (width - panel) / 2;
        search = new EditBox(font, left, 30, panel, 20, Component.literal("Search items"));
        search.setHint(Component.literal("Search items or potion variants…")); search.setValue(query);
        search.setResponder(value -> { query = value; refreshGrid(); }); addRenderableWidget(search);
        String[] filters = {"All items", "Selected only", "In inventory"};
        int w = (panel - 8) / 3;
        for (int i = 0; i < 3; i++) {
            int index = i;
            var b = addRenderableWidget(Button.builder(Component.literal(filters[i]), button -> {
                filter = index; rebuildWidgets();
            }).bounds(left + i * (w + 4), 54, w, 20).build()); b.active = i != filter;
        }
        addRenderableWidget(Button.builder(Component.literal("Reset defaults"), b -> {
            selected.clear(); selected.addAll(defaults); refreshGrid();
        }).bounds(left, height - 28, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            apply.accept(List.copyOf(selected)); minecraft.gui.setScreen(parent);
        }).bounds(left + w + 4, height - 28, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(left + 2 * (w + 4), height - 28, w, 20).build());
        refreshGrid();
    }
    private void refreshGrid() {
        for (var widget : gridWidgets) removeWidget(widget);
        gridWidgets.clear();
        var inventory = new HashSet<SurvivalItemRule>();
        for (var stack : SurvivalItems.carried(minecraft.player)) if (!stack.isEmpty()) inventory.add(SurvivalItems.rule(stack));
        for (var rule : inventory) if (!catalog.contains(rule)) catalog.add(rule);
        var rows = LinearLayout.vertical().spacing(4);
        LinearLayout row = null;
        int count = 0, columns = Math.max(1, panel / 40);
        String term = query.toLowerCase(Locale.ROOT).strip();
        for (var rule : catalog) {
            if (filter == 1 && !selected.contains(rule) || filter == 2 && !inventory.contains(rule)) continue;
            if (!(SurvivalItems.name(rule) + " " + rule.item()).toLowerCase(Locale.ROOT).contains(term)) continue;
            if (count++ % columns == 0) { row = LinearLayout.horizontal().spacing(4); rows.addChild(row); }
            row.addChild(new SurvivalItemButton(rule, () -> selected.contains(rule), () -> {
                if (!selected.remove(rule)) selected.add(rule);
                if (filter == 1) refreshGrid();
            }));
        }
        if (count == 0) rows.addChild(new StringWidget(Component.literal("No matching items"), font));
        var scroll = new ScrollableLayout(minecraft, rows, Math.max(32, height - 132));
        scroll.setMinWidth(panel); scroll.arrangeElements(); scroll.setX((width - scroll.getWidth()) / 2); scroll.setY(80);
        scroll.visitWidgets(widget -> { gridWidgets.add(widget); addRenderableWidget(widget); });
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        g.centeredText(font, title, width / 2, 12, 0xffffffff);
        g.centeredText(font, selected.size() + " selected • Click icons to toggle • Save in settings to apply", width / 2, height - 43, 0xffbbbbbb);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
