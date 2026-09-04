package dev.macepvpmod;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.client.gui.components.*;

public final class SurvivalItemsSmokeTest implements FabricClientGameTest {
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private static java.util.stream.Stream<net.minecraft.client.gui.components.events.GuiEventListener> descendants(
            net.minecraft.client.gui.components.events.ContainerEventHandler parent) {
        return parent.children().stream().flatMap(child -> java.util.stream.Stream.concat(java.util.stream.Stream.of(child),
                child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container
                        ? descendants(container) : java.util.stream.Stream.empty()));
    }
    private static void click(net.minecraft.client.Minecraft mc, String label) {
        descendants(mc.gui.screen()).filter(w -> w instanceof Button b && b.getMessage().getString().equals(label))
                .map(Button.class::cast).findFirst().orElseThrow().onPress(null);
    }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(mc -> {
                var p = mc.player; var c = SurvivalConfig.defaults();
                p.getInventory().clearContent();
                check(!SurvivalItems.scan(p, c).healing(), "Empty inventory qualifies");
                p.getInventory().setItem(0, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_HEALING));
                check(SurvivalItems.scan(p, c).healing(), "Splash healing not detected");
                p.getInventory().setItem(0, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.POISON));
                check(!SurvivalItems.scan(p, c).healing(), "Poison qualifies");
                p.getInventory().setItem(0, PotionContents.createItemStack(Items.LINGERING_POTION, Potions.LONG_REGENERATION));
                check(SurvivalItems.scan(p, c).healing(), "Lingering regeneration not detected");
                p.getInventory().setItem(0, ItemStack.EMPTY);
                p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.COOKED_BEEF));
                check(SurvivalItems.scan(p, c).saturation(), "Offhand food not detected");
                p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                check(!SurvivalItems.scan(p, c).saturation(), "Consumed food still counted");
                p.getInventory().setItem(10, new ItemStack(Items.TOTEM_OF_UNDYING));
                check(SurvivalItems.scan(p, c).totem(), "Retotem scan regressed");
                for (var rule : c.healingItems()) check(SurvivalItems.rule(SurvivalItems.icon(rule)).equals(rule), "Potion round trip failed");
                p.setHealth(5);
                check((SurvivalState.healingState(5, 20, 20, c) & 1) != 0, "Raw audio health state was gated");
            });
            context.runOnClient(mc -> {
                var applied = new java.util.concurrent.atomic.AtomicReference<java.util.List<SurvivalItemRule>>();
                mc.gui.setScreen(new SurvivalItemEditor(null, "Healing items", SurvivalItemRule.healingDefaults(),
                        SurvivalItemRule.healingDefaults(), applied::set));
                click(mc, "Selected only");
                var icon = descendants(mc.gui.screen()).filter(SurvivalItemButton.class::isInstance).map(Button.class::cast).findFirst().orElseThrow();
                icon.onPress(null);
                click(mc, "Done");
                check(applied.get().size() == 14, "Selection removal failed");
                mc.gui.setScreen(new SurvivalItemEditor(null, "Healing items", applied.get(), SurvivalItemRule.healingDefaults(), applied::set));
                click(mc, "Reset defaults"); click(mc, "Cancel");
                check(applied.get().size() == 14, "Cancel leaked reset");
                mc.gui.setScreen(new SurvivalItemEditor(null, "Healing items", applied.get(), SurvivalItemRule.healingDefaults(), applied::set));
                click(mc, "Reset defaults"); click(mc, "Done");
                check(applied.get().size() == 15, "Reset failed");
                mc.gui.setScreen(new SurvivalItemEditor(null, "Healing items", applied.get(), SurvivalItemRule.healingDefaults(), applied::set));
                click(mc, "In inventory");
                check(descendants(mc.gui.screen()).filter(SurvivalItemButton.class::isInstance).count() == 1, "Inventory filter failed");
            });
            for (int scale : new int[]{1, 2, 3}) {
                context.runOnClient(mc -> {
                    mc.options.guiScale().set(scale); mc.resizeGui();
                    mc.gui.setScreen(new SurvivalItemEditor(null, "Healing items", SurvivalItemRule.healingDefaults(),
                            SurvivalItemRule.healingDefaults(), result -> {}));
                });
                context.waitTicks(2); context.takeScreenshot("survival-items-scale-" + scale);
                context.runOnClient(mc -> mc.gui.screen().mouseScrolled(mc.gui.screen().width / 2.0, 100, 0, -4));
                context.waitTick(); context.takeScreenshot("survival-scrolled-scale-" + scale);
                context.runOnClient(mc -> {
                    var search = mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                    search.setValue("strong healing");
                });
                context.waitTick(); context.takeScreenshot("survival-search-scale-" + scale);
                context.runOnClient(mc -> {
                    mc.gui.setScreen(new SurvivalSettingsScreen(null)); click(mc, "Healing");
                });
                context.waitTick(); context.takeScreenshot("survival-settings-scale-" + scale);
            }
            context.setScreen(() -> null);
        }
    }
}
