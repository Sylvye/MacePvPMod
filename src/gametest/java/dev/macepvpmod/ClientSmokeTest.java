package dev.macepvpmod;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;

public final class ClientSmokeTest implements FabricClientGameTest {
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private static void click(ClientGameTestContext context, String label) {
        context.runOnClient(mc -> {
            var button = mc.gui.screen().children().stream().filter(w -> w instanceof Button b && b.getMessage().getString().equals(label))
                    .map(w -> (Button)w).findFirst().orElseThrow();
            button.onPress(null);
        });
    }
    private static void saveDefaults() {
        try { MacePvPMod.CONFIG.save(PitchConfig.defaults()); }
        catch (java.io.IOException e) { throw new RuntimeException(e); }
    }
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> saveDefaults());
        context.setScreen(() -> new PitchSettingsScreen(null));
        context.waitTicks(2);
        context.takeScreenshot("settings-basic");
        click(context, "Enabled: On");
        context.runOnClient(mc -> check(MacePvPMod.CONFIG.current().enabled(), "Draft leaked into active settings"));
        click(context, "Cancel");
        context.setScreen(() -> new PitchSettingsScreen(null));
        context.runOnClient(mc -> {
            var hex = mc.gui.screen().children().stream().filter(w -> w instanceof EditBox).map(w -> (EditBox)w).findFirst().orElseThrow();
            hex.setValue("oops");
            var save = mc.gui.screen().children().stream().filter(w -> w instanceof Button b && b.getMessage().getString().equals("Save")).map(w -> (Button)w).findFirst().orElseThrow();
            check(!save.active, "Invalid color should block Save"); hex.setValue("AABBCC"); check(save.active, "Valid color should allow Save");
        });
        click(context, "Save");
        context.runOnClient(mc -> check(MacePvPMod.CONFIG.current().color() == 0xAABBCC, "Color was not saved"));
        context.setScreen(() -> new PitchSettingsScreen(null));
        click(context, "Reset defaults"); click(context, "Save");
        context.runOnClient(mc -> check(MacePvPMod.CONFIG.current().equals(PitchConfig.defaults()), "Reset failed"));
        context.setScreen(() -> new PitchSettingsScreen(null));
        click(context, "Advanced settings"); context.waitTick(); context.takeScreenshot("settings-advanced");
        click(context, "Third person: Off"); click(context, "Save");
        context.runOnClient(mc -> {
            check(MacePvPMod.CONFIG.current().thirdPerson(), "Advanced settings not saved");
            saveDefaults();
        });
        context.setScreen(() -> new SettingsScreen(null));
        click(context, "Damage Counter");
        context.waitTick(); context.takeScreenshot("damage-fall-settings");
        click(context, "Enabled: On");
        click(context, "Mace hit damage");
        click(context, "Damage: Reported");
        context.waitTick(); context.takeScreenshot("damage-calculated-settings");
        context.runOnClient(mc -> check(!MacePvPMod.DAMAGE_CONFIG.current().calculatedDamage(), "Mode draft leaked"));
        click(context, "Enabled: On");
        click(context, "Save");
        context.runOnClient(mc -> {
            check(MacePvPMod.DAMAGE_CONFIG.current().calculatedDamage(), "Mode was not saved");
            check(!MacePvPMod.DAMAGE_CONFIG.current().fallEnabled(), "Fall toggle not saved");
            check(!MacePvPMod.DAMAGE_CONFIG.current().hitEnabled(), "Hit toggle not saved");
        });
        click(context, "Damage Counter"); click(context, "Reset damage defaults");
        click(context, "Preview position"); context.waitTick(); context.takeScreenshot("damage-preview");
        click(context, "Back to settings"); click(context, "Save");
        context.runOnClient(mc -> check(MacePvPMod.DAMAGE_CONFIG.current().equals(DamageConfig.defaults()), "Damage reset failed"));
        context.setScreen(() -> new SettingsScreen(null));
        click(context, "Attribute Swaps");
        context.runOnClient(mc -> {
            var input = mc.gui.screen().children().stream().filter(w -> w instanceof EditBox).map(w -> (EditBox)w).findFirst().orElseThrow();
            input.setValue("minecraft:missing_sound");
            var save = mc.gui.screen().children().stream().filter(w -> w instanceof Button b && b.getMessage().getString().equals("Save")).map(w -> (Button)w).findFirst().orElseThrow();
            check(!save.active, "Unknown sound accepted");
            input.setValue("minecraft:block.note_block.pling");
            check(save.active, "Valid sound rejected");
        });
        click(context, "Actionbar: On"); click(context, "Sound: On"); click(context, "Save");
        context.runOnClient(mc -> {
            var c = MacePvPMod.ATTRIBUTE_SWAP_CONFIG.current();
            check(!c.visualEnabled() && !c.soundEnabled(), "Feedback toggles not saved");
            check(c.soundId().equals("minecraft:block.note_block.pling"), "Sound not saved");
        });
        click(context, "Attribute Swaps"); click(context, "Reset defaults"); click(context, "Save");
        context.setScreen(() -> null);
    }
}
