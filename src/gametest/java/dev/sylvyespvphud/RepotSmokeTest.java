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
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
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

    private static void toggle(Screen screen,String label) {
        var text=descendants(screen).filter(w->w instanceof StringWidget s && s.getMessage().getString().equals(label))
                .map(StringWidget.class::cast).findFirst().orElseThrow();
        descendants(screen).filter(w->w instanceof Button b && b.getX()>text.getX() && Math.abs(b.getY()-text.getY())<=1)
                .map(Button.class::cast).findFirst().orElseThrow().onPress(null);
    }

    private static final class AttentionPreview extends Screen {
        AttentionPreview(){super(net.minecraft.network.chat.Component.literal("Repot attention verification"));}
        @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g,int mx,int my,float dt) {
            g.fill(0,0,width,height,0xff101820);
            var config=RepotConfig.defaults();
            var effects=List.of(new RepotState.Warning("minecraft:speed",100,false),new RepotState.Warning("minecraft:strength",0,true));
            g.centeredText(font,"Low duration + ended effect",width/2,15,0xffffffff);
            RepotHud.renderWarnings(g,config,effects,HudStyle.text(.5,0,0,0,40,2,0xffff55),width,height,false,0);
            var text=new RepotConfig(true,config.effects(),15,10,false,4,config.template(),config.attention());
            RepotHud.renderWarnings(g,text,effects,HudStyle.text(.5,0,0,0,95,1,0xffff55),width,height,false,0);
            g.centeredText(font,"Dim pulse stays visible",width/2,130,0xffffffff);
            RepotHud.renderWarnings(g,config,effects,HudStyle.text(.5,0,0,0,150,2,0xffff55),width,height,false,.5);
            RepotHud.renderWarnings(g,text,effects,HudStyle.text(.5,0,0,0,205,1,0xffff55),width,height,false,.5);
        }
    }

    private static void clearEffects(net.minecraft.world.entity.player.Player player) {
        for (var effect:List.copyOf(player.getActiveEffects())) player.removeEffect(effect.getEffect());
    }

    private static void packetChecks(net.minecraft.client.Minecraft mc) {
        var p=mc.player;
        for (int order=0;order<2;order++) {
            clearEffects(p);RepotHud.reset();
            p.getInventory().setItem(0,PotionContents.createItemStack(Items.POTION,Potions.SWIFTNESS));
            p.addEffect(new MobEffectInstance(MobEffects.SPEED,1200));RepotHud.tick(mc);
            check(RepotHud.warnings().isEmpty(),"Long effect warned before totem");
            if(order==0) {
                mc.getConnection().handleRemoveMobEffect(new ClientboundRemoveMobEffectPacket(p.getId(),MobEffects.SPEED));
                RepotHud.tick(mc);check(RepotHud.warnings().isEmpty(),"Unconfirmed removal warned");
                mc.getConnection().handleEntityEvent(new ClientboundEntityEventPacket(p,(byte)35));
            } else {
                mc.getConnection().handleEntityEvent(new ClientboundEntityEventPacket(p,(byte)35));
                RepotHud.tick(mc);check(RepotHud.warnings().isEmpty(),"Retained effect treated as ended");
                mc.getConnection().handleRemoveMobEffect(new ClientboundRemoveMobEffectPacket(p.getId(),MobEffects.SPEED));
            }
            RepotHud.tick(mc);
            check(RepotHud.warnings().equals(List.of("minecraft:speed")),"Totem packet order "+order+" missed clear");
            check(RepotHud.details().getFirst().ended(),"Totem clear not marked ended");
            check(RepotHud.period(SylvyesPvPHud.SURVIVAL_CONFIG.current().repot(),RepotHud.details())==.35,"Ended effect not at fastest pulse");
            mc.getConnection().handleUpdateMobEffect(new ClientboundUpdateMobEffectPacket(p.getId(),new MobEffectInstance(MobEffects.SPEED,1200),false));
            RepotHud.tick(mc);check(RepotHud.warnings().isEmpty(),"Reapplied effect retained grace");
        }
        clearEffects(p);RepotHud.reset();
        p.addEffect(new MobEffectInstance(MobEffects.SPEED,1200,0,true,true));
        check(p.getEffect(MobEffects.SPEED).isAmbient(),"Ambient fixture was not applied");RepotHud.tick(mc);
        mc.getConnection().handleRemoveMobEffect(new ClientboundRemoveMobEffectPacket(p.getId(),MobEffects.SPEED));
        mc.getConnection().handleEntityEvent(new ClientboundEntityEventPacket(p,(byte)35));RepotHud.tick(mc);
        check(RepotHud.warnings().isEmpty(),"Totem clear counted ambient effect");
        clearEffects(p);RepotHud.reset();
        p.addEffect(new MobEffectInstance(MobEffects.SPEED,1200));RepotHud.tick(mc);
        mc.getConnection().handleRemoveMobEffect(new ClientboundRemoveMobEffectPacket(p.getId(),MobEffects.SPEED));
        var other=new net.minecraft.client.player.RemotePlayer(mc.level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"RepotOther"));
        other.setId(2000030);mc.level.addEntity(other);
        mc.getConnection().handleEntityEvent(new ClientboundEntityEventPacket(other,(byte)35));RepotHud.tick(mc);
        check(RepotHud.warnings().isEmpty(),"Other player's totem started local grace");
        mc.level.removeEntity(other.getId(),net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        RepotHud.reset();
        var config=SylvyesPvPHud.SURVIVAL_CONFIG.current().repot();
        var mixed=List.of(new RepotState.Warning("minecraft:speed",100,false),new RepotState.Warning("minecraft:strength",0,true));
        var message=RepotHud.message(config,mixed,0xffff55);
        check(message.getString().equals("Repot Speed, Strength"),"Styled message changed text");
        var colors=new java.util.HashMap<String,Integer>();
        message.visit((style,text)->{if(style.getColor()!=null)colors.put(text,style.getColor().getValue());return Optional.<Boolean>empty();},net.minecraft.network.chat.Style.EMPTY);
        check(colors.get("Speed")==0xffff55&&colors.get("Strength")==0xff3333,"Mixed text colors incorrect");
        var session=new SettingsSession();session.repot("graceSeconds",0);SylvyesPvPHud.SURVIVAL_CONFIG.activate(session.survival);
        p.addEffect(new MobEffectInstance(MobEffects.SPEED,1200));RepotHud.tick(mc);
        mc.getConnection().handleRemoveMobEffect(new ClientboundRemoveMobEffectPacket(p.getId(),MobEffects.SPEED));
        mc.getConnection().handleEntityEvent(new ClientboundEntityEventPacket(p,(byte)35));RepotHud.tick(mc);
        check(RepotHud.warnings().isEmpty(),"Zero grace still warned after totem");
        SylvyesPvPHud.SURVIVAL_CONFIG.activate(SurvivalConfig.defaults());clearEffects(p);RepotHud.reset();
    }

    @Override public void runTest(ClientGameTestContext context) {
        AtomicReference<ProfileSettings> original = new AtomicReference<>();
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(mc -> {
                original.set(ProfileSettings.current());
                SylvyesPvPHud.SURVIVAL_CONFIG.activate(SurvivalConfig.defaults());
                var p = mc.player; p.getInventory().clearContent(); clearEffects(p);
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
                packetChecks(mc);
                clearEffects(p);
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
                    check(session.survival.repot().attention().pulsing(),"Pulsing default missing");
                    toggle(mc.gui.screen(),"Pulsing");
                    check(!session.survival.repot().attention().pulsing(),"Pulse toggle did not change draft");
                    toggle(mc.gui.screen(),"Change color when ended");
                    check(!session.survival.repot().attention().endedColorEnabled(),"Ended color toggle failed");
                    toggle(mc.gui.screen(),"Change color when ended");
                    session.repotAttention("endedColor",0x12ff88);
                    check(SylvyesPvPHud.SURVIVAL_CONFIG.current().repot().attention().pulsing(),"Attention draft leaked");
                    session.repotAttention("fastInterval",2.0);check(!session.validation().isEmpty(),"Invalid pulse intervals accepted");
                    session.repotAttention("fastInterval",.35);check(session.validation().isEmpty(),"Valid pulse intervals rejected");
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
                context.runOnClient(mc -> mc.gui.screen().mouseScrolled(mc.gui.screen().width-20,110,0,-100));
                context.waitTick(); context.takeScreenshot("repot-attention-settings-"+scale);
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
                    click(mc.gui.screen(),"HUD Studio");click(mc.gui.screen(),"Repot warning");
                    click(mc.gui.screen(),"Low duration");check(session.repotPreviewState==1,"Ended preview missing");
                    click(mc.gui.screen(),"Ended");check(session.repotPreviewState==2,"Mixed preview missing");
                    mc.gui.setScreen(new HudPlacementScreen(settings,session,8));
                });
                context.waitTick(); context.takeScreenshot("repot-icons-"+scale);
                context.runOnClient(mc -> {
                    mc.gui.screen().onClose();
                    var settings=(SettingsScreen)mc.gui.screen(); var session=draft(settings);
                    session.repot("icons",false);
                    session.repotPreviewState=2;
                    mc.gui.setScreen(new HudPlacementScreen(settings,session,8));
                });
                context.waitTick(); context.takeScreenshot("repot-text-"+scale);
                context.runOnClient(mc -> {
                    mc.gui.screen().onClose(); var session=draft((SettingsScreen)mc.gui.screen());
                    if (scale==3) {
                        try { session.apply(); } catch (java.io.IOException e) { throw new AssertionError(e); }
                        check(!SylvyesPvPHud.SURVIVAL_CONFIG.current().repot().icons(),"Apply lost display mode");
                        check(!SylvyesPvPHud.SURVIVAL_CONFIG.current().repot().attention().pulsing(),"Apply lost pulse toggle");
                        check(SylvyesPvPHud.SURVIVAL_CONFIG.current().repot().attention().endedColor()==0x12ff88,"Apply lost ended color");
                    }
                    mc.gui.setScreen(null);
                });
            }
            context.runOnClient(mc->{mc.options.guiScale().set(1);mc.resizeGui();mc.gui.setScreen(new AttentionPreview());});
            context.waitTick();context.takeScreenshot("repot-attention-phases");
            context.runOnClient(mc -> {
                try { SylvyesPvPHud.PROFILES.updateActive(original.get()); }
                catch (java.io.IOException e) { throw new AssertionError(e); }
                mc.options.guiScale().set(2); mc.resizeGui(); RepotHud.reset();
                mc.gui.setScreen(null);
            });
        }
    }
}
