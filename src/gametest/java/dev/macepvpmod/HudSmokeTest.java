package dev.macepvpmod;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class HudSmokeTest implements FabricClientGameTest {
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(mc -> {
                var p = mc.player;
                var defaults = PitchConfig.defaults();
                AttributeSwaps.click();
                AttributeSwaps.selected(p.getInventory(), (p.getInventory().getSelectedSlot() + 1) % 9);
                check(AttributeSwaps.shouldRender(mc), "Swap should show HUD text");
                mc.gui.hud.toggle(); check(!AttributeSwaps.shouldRender(mc), "F1 must hide swap HUD"); mc.gui.hud.toggle();
                mc.gui.setScreen(new SettingsScreen(null)); check(!AttributeSwaps.shouldRender(mc), "Menus must hide swap HUD"); mc.gui.setScreen(null);
                for (int i = 0; i < 60; i++) AttributeSwaps.endTick();
                check(!AttributeSwaps.shouldRender(mc), "Swap HUD should expire after three seconds");
                check(!DamageHud.showFall(1.5), "Threshold must be strictly above 1.5");
                check(DamageHud.showFall(1.51), "Fall distance should appear above threshold");
                check(!DamageHud.showFall(0), "Reset fall distance should hide");
                DamageHud.tick(mc);
                var zombie = new net.minecraft.world.entity.monster.zombie.Zombie(mc.level);
                zombie.setId(2000000); zombie.setHealth(20); mc.level.addEntity(zombie);
                p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.MACE));
                DamageHud.attacked(zombie);
                zombie.setHealth(12);
                DamageHud.tick(mc);
                check(DamageHud.visibleHit().isEmpty(), "Unconfirmed health loss must not display");
                mc.getConnection().handleDamageEvent(new net.minecraft.network.protocol.game.ClientboundDamageEventPacket(zombie, p.damageSources().playerAttack(p)));
                DamageHud.tick(mc);
                check(DamageHud.visibleHit().equals("8.0 damage"), "Confirmed health delta must display");
                for (int tick = 0; tick < 60; tick++) DamageHud.tick(mc);
                check(DamageHud.visibleHit().isEmpty(), "Hit display should expire");
                DamageHud.attacked(zombie);
                mc.getConnection().handleDamageEvent(new net.minecraft.network.protocol.game.ClientboundDamageEventPacket(zombie, p.damageSources().playerAttack(p)));
                for (int tick = 0; tick < 20; tick++) DamageHud.tick(mc);
                check(DamageHud.visibleHit().equals("Damage unavailable"), "Missing health update must not fabricate damage");
                for (int tick = 0; tick < 60; tick++) DamageHud.tick(mc);
                p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
                DamageHud.attacked(zombie); zombie.setHealth(10);
                mc.getConnection().handleDamageEvent(new net.minecraft.network.protocol.game.ClientboundDamageEventPacket(zombie, p.damageSources().playerAttack(p)));
                DamageHud.tick(mc);
                check(DamageHud.visibleHit().isEmpty(), "Non-mace attacks must not display");
                try { MacePvPMod.DAMAGE_CONFIG.save(new DamageConfig(1, true, 0xffffff, 1, 0, 14, true, 0xff6666, 1, 0, 28, 3, true, "{blocks} blocks", "{damage} damage / {blocks} blocks")); }
                catch (java.io.IOException e) { throw new RuntimeException(e); }
                p.setSprinting(true); p.fallDistance = 8;
                var plain = new ItemStack(Items.MACE);
                p.setItemSlot(EquipmentSlot.MAINHAND, plain);
                double base = MaceDamageCalculator.atAttack(p);
                var enchantments = mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                var dense = new ItemStack(Items.MACE);
                dense.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.DENSITY), 5);
                p.setItemSlot(EquipmentSlot.MAINHAND, dense);
                double expected = MaceDamageCalculator.atAttack(p);
                check(Math.abs(expected - base - 20) < .001, "Density V must add 20 raw damage at 8 blocks");
                var breach = new ItemStack(Items.MACE);
                breach.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.BREACH), 4);
                p.setItemSlot(EquipmentSlot.MAINHAND, breach);
                check(Math.abs(MaceDamageCalculator.atAttack(p) - base) < .001, "Breach must not change raw damage");
                p.setItemSlot(EquipmentSlot.MAINHAND, dense);
                DamageHud.attacked(zombie);
                DamageHud.tick(mc);
                check(DamageHud.visibleHit().isEmpty(), "Calculated attacks must still be confirmed");
                p.fallDistance = 0; p.setItemSlot(EquipmentSlot.MAINHAND, plain);
                mc.getConnection().handleDamageEvent(new net.minecraft.network.protocol.game.ClientboundDamageEventPacket(zombie, p.damageSources().playerAttack(p)));
                DamageHud.tick(mc);
                check(DamageHud.visibleHit().equals(String.format(java.util.Locale.ROOT, "%.1f damage / 8.0 blocks", expected)),
                        "Calculated damage must use attack-time fall/enchantment snapshot without health loss");
                p.setSprinting(false);
                try { MacePvPMod.DAMAGE_CONFIG.save(DamageConfig.defaults()); }
                catch (java.io.IOException e) { throw new RuntimeException(e); }
                mc.level.removeEntity(zombie.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                check(!PitchHud.shouldRender(mc, defaults), "Grounded guide should be hidden");
                p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                p.setPos(p.getX(), p.getY() + 40, p.getZ());
                p.setOnGround(false); p.startFallFlying(); p.setXRot(40); p.xRotO = 40;
                check(PitchHud.shouldRender(mc, defaults), "Gliding guide should be visible");
                mc.gui.hud.toggle(); check(!PitchHud.shouldRender(mc, defaults), "F1 should hide guide"); mc.gui.hud.toggle();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                check(!PitchHud.shouldRender(mc, defaults), "Third person should be hidden by default");
                var third = new PitchConfig(1, true, 100, 1, 0x999999, .4, 40, 2, 60, true);
                check(PitchHud.shouldRender(mc, third), "Third-person option ignored");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                p.stopFallFlying(); check(!PitchHud.shouldRender(mc, defaults), "Stopping glide should hide guide"); p.startFallFlying();
                p.setOnGround(true); check(!PitchHud.shouldRender(mc, defaults), "Landing should hide guide"); p.setOnGround(false);
                mc.gui.setScreen(new SettingsScreen(null)); check(!PitchHud.shouldRender(mc, defaults), "Menus should hide guide"); mc.gui.setScreen(null);
                p.setHealth(0); check(!PitchHud.shouldRender(mc, defaults), "Dead players should not see guide"); p.setHealth(20);
            });
            context.takeScreenshot("gliding-pitch-40");
            for (int scale : new int[]{1, 2, 3}) {
                context.runOnClient(mc -> { mc.options.guiScale().set(scale); mc.resizeGui(); mc.gui.setScreen(new SettingsScreen(null)); });
                context.waitTick(); context.takeScreenshot("settings-scale-" + scale);
            }
            context.setScreen(() -> null);
        }
        context.runOnClient(mc -> check(!PitchHud.shouldRender(mc, PitchConfig.defaults()), "Disconnected guide should be hidden"));
    }
}
