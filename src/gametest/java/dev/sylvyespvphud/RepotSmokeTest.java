package dev.sylvyespvphud;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.component.ItemContainerContents;

public final class RepotSmokeTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static java.util.stream.Stream<GuiEventListener> descendants(ContainerEventHandler parent) {
        return parent.children().stream().flatMap(w -> java.util.stream.Stream.concat(java.util.stream.Stream.of(w),
                w instanceof ContainerEventHandler container ? descendants(container) : java.util.stream.Stream.empty()));
    }
    private static void click(Screen screen, String label) {
        descendants(screen).filter(w -> w instanceof Button b && b.getMessage().getString().equals(label))
                .map(Button.class::cast).findFirst().orElseThrow(() -> new AssertionError("Missing " + label)).onPress(null);
    }
    private static SettingsSession draft(SettingsScreen screen) {
        try { var field=SettingsScreen.class.getDeclaredField("draft"); field.setAccessible(true); return (SettingsSession)field.get(screen); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @Override public void runTest(ClientGameTestContext context) {
        AtomicReference<ProfileSettings> original = new AtomicReference<>();
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(mc -> {
                original.set(ProfileSettings.current());
                SylvyesPvPHud.SURVIVAL_CONFIG.activate(SurvivalConfig.defaults());
                var p = mc.player; p.getInventory().clearContent(); p.removeAllEffects();
                for (var item : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
                    p.getInventory().setItem(12, PotionContents.createItemStack(item, Potions.STRONG_SWIFTNESS));
                    check(RepotHud.potions(p).equals(Set.of("minecraft:speed")), "Potion form/strength not matched");
                }
                p.getInventory().clearContent();
                p.setItemSlot(EquipmentSlot.OFFHAND, PotionContents.createItemStack(Items.POTION, Potions.LONG_SWIFTNESS));
                check(RepotHud.potions(p).contains("minecraft:speed"), "Offhand not scanned");
                p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                p.getInventory().setItem(0, PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.SWIFTNESS));
                check(RepotHud.potions(p).isEmpty(), "Arrow counted as potion");
                var box = new ItemStack(Items.SHULKER_BOX);
                box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS))));
                p.getInventory().setItem(0, box);
                check(RepotHud.potions(p).isEmpty(), "Nested potion counted");
                var potion = new ItemStack(Items.SPLASH_POTION);
                potion.set(DataComponents.POTION_CONTENTS, PotionContents.EMPTY
                        .withEffectAdded(new MobEffectInstance(MobEffects.SPEED, 100, 1))
                        .withEffectAdded(new MobEffectInstance(MobEffects.STRENGTH, 100)));
                p.getInventory().setItem(0, potion);
                check(RepotHud.potions(p).equals(Set.of("minecraft:speed", "minecraft:strength")), "Custom effects not matched");
                p.addEffect(new MobEffectInstance(MobEffects.SPEED, 299));
                p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 299)); RepotHud.tick(mc);
                check(RepotHud.warnings().equals(List.of("minecraft:speed", "minecraft:strength")), "Simultaneous warnings/order");
                p.removeEffect(MobEffects.SPEED); p.addEffect(new MobEffectInstance(MobEffects.SPEED, 1, 0, true, true));
                RepotHud.tick(mc); check(!RepotHud.warnings().contains("minecraft:speed"), "Ambient effect warned");
                p.removeEffect(MobEffects.SPEED); RepotHud.tick(mc);
                check(!RepotHud.warnings().contains("minecraft:speed"), "Ambient removal left grace");
                p.addEffect(new MobEffectInstance(MobEffects.SPEED, 1)); RepotHud.tick(mc);
                p.removeEffect(MobEffects.SPEED); RepotHud.tick(mc);
                check(RepotHud.warnings().contains("minecraft:speed"), "Natural expiry missing grace");
                p.getInventory().clearContent(); RepotHud.tick(mc);
                check(RepotHud.warnings().isEmpty(), "Warning outlived carried potion");
                p.getInventory().setItem(0,potion); RepotHud.tick(mc);
                check(RepotHud.warnings().contains("minecraft:speed"), "Grace unavailable after potion restored");
                ProfileSettings.current().activate(); RepotHud.tick(mc);
                check(!RepotHud.warnings().contains("minecraft:speed"), "Profile activation did not clear grace");
                RepotHud.reset(); check(RepotHud.warnings().isEmpty(), "Reset failed");
                p.removeAllEffects();
            });

            for (int scale : new int[]{1,2,3}) {
                context.runOnClient(mc -> {
                    mc.options.guiScale().set(scale); mc.resizeGui();
                    mc.gui.setScreen(new SettingsScreen(null));
                    click(mc.gui.screen(),"Survival"); click(mc.gui.screen(),"Repot Warning");
                    click(mc.gui.screen(),"Edit 4");
                    check(mc.gui.screen() instanceof RepotEffectScreen,"Repot tab did not open selector");
                    click(mc.gui.screen(),"Cancel");
                });
                context.waitTicks(2); context.takeScreenshot("repot-settings-"+scale);
                context.runOnClient(mc -> {
                    var session=draft((SettingsScreen)mc.gui.screen());
                    click(mc.gui.screen(),"Effect icons"); check(!session.survival.repot().icons(),"Display toggle failed");
                    var message=descendants(mc.gui.screen()).filter(EditBox.class::isInstance).map(EditBox.class::cast)
                            .filter(box->box.getValue().equals("Repot {effect}")).findFirst().orElseThrow();
                    message.setValue("Repot {wrong}"); check(!session.validation().isEmpty(),"Invalid message accepted");
                    message.setValue("Repot {effect}"); check(session.validation().isEmpty(),"Valid message rejected");
                    click(mc.gui.screen(),"Text"); check(session.survival.repot().icons(),"Icon toggle failed");
                    session.repot("thresholdSeconds",35); session.toggle(4); session.toggle(4);
                    check(session.survival.repot().thresholdSeconds()==35,"Module toggle reset custom settings");
                    session.repot("thresholdSeconds",15);
                    mc.gui.screen().mouseScrolled(mc.gui.screen().width-20,110,0,-5);
                });
                context.waitTick(); context.takeScreenshot("repot-settings-scrolled-"+scale);
                context.runOnClient(mc -> {
                    var settings = (SettingsScreen)mc.gui.screen();
                    var session = draft(settings);
                    var applied = new AtomicReference<List<String>>();
                    mc.gui.setScreen(new RepotEffectScreen(settings,session.survival.repot().effects(),applied::set));
                    click(mc.gui.screen(),"✓ Speed"); click(mc.gui.screen(),"Cancel");
                    check(applied.get()==null,"Selector cancel applied edits");
                    mc.gui.setScreen(new RepotEffectScreen(settings,session.survival.repot().effects(),effects->{session.repot("effects",effects);applied.set(effects);}));
                    click(mc.gui.screen(),"✓ Speed"); click(mc.gui.screen(),"Done");
                    check(!session.survival.repot().effects().contains("minecraft:speed"),"Selector done lost edits");
                    check(SylvyesPvPHud.SURVIVAL_CONFIG.current().repot().effects().contains("minecraft:speed"),"Draft leaked into live settings");
                    session.repot("effects", RepotConfig.defaults().effects());
                    mc.gui.setScreen(new RepotEffectScreen(settings,session.survival.repot().effects(),effects->session.repot("effects",effects)));
                    var search=mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                    search.setValue("weaving");
                    check(descendants(mc.gui.screen()).filter(w->w instanceof Button b && b.getMessage().getString().equals("✓ Weaving")).count()==1,"Effect search failed");
                });
                context.waitTick(); context.takeScreenshot("repot-selector-"+scale);
                context.runOnClient(mc -> {
                    click(mc.gui.screen(),"Cancel");
                    var settings=(SettingsScreen)mc.gui.screen(); var session=draft(settings);
                    session.repot("icons",true);
                    mc.gui.setScreen(new HudPlacementScreen(settings,session,8));
                });
                context.waitTick(); context.takeScreenshot("repot-icons-"+scale);
                context.runOnClient(mc -> {
                    mc.gui.screen().onClose();
                    var settings=(SettingsScreen)mc.gui.screen(); var session=draft(settings);
                    session.repot("icons",false);
                    mc.gui.setScreen(new HudPlacementScreen(settings,session,8));
                });
                context.waitTick(); context.takeScreenshot("repot-text-"+scale);
                context.runOnClient(mc -> {
                    mc.gui.screen().onClose(); var session=draft((SettingsScreen)mc.gui.screen());
                    if (scale==3) {
                        try { session.apply(); } catch (java.io.IOException e) { throw new AssertionError(e); }
                        check(!SylvyesPvPHud.SURVIVAL_CONFIG.current().repot().icons(),"Apply lost display mode");
                    }
                    mc.gui.setScreen(null);
                });
            }
            context.runOnClient(mc -> {
                try { SylvyesPvPHud.PROFILES.updateActive(original.get()); }
                catch (java.io.IOException e) { throw new AssertionError(e); }
                mc.options.guiScale().set(2); mc.resizeGui(); RepotHud.reset();
            });
        }
    }
}
