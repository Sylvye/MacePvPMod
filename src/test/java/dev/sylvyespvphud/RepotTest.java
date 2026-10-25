package dev.sylvyespvphud;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RepotTest {
    private static final String SPEED = "minecraft:speed", STRENGTH = "minecraft:strength";
    private static final Set<String> POTIONS = Set.of(SPEED, STRENGTH);
    private final RepotState state = new RepotState();
    private RepotConfig config(int threshold, int grace) {
        return new RepotConfig(true, List.of(SPEED, STRENGTH), threshold, grace, true, 4, "Repot {effect}");
    }
    private Map<String, RepotState.Effect> effect(int ticks, boolean ambient, boolean infinite) {
        return Map.of(SPEED, new RepotState.Effect(ticks, ambient, infinite));
    }

    @Test void strictThresholdAndPotionAvailability() {
        var c = config(15, 10);
        assertTrue(state.tick(c, effect(300, false, false), POTIONS).isEmpty());
        assertEquals(List.of(SPEED), state.tick(c, effect(299, false, false), POTIONS));
        assertTrue(state.tick(c, effect(298, false, false), Set.of()).isEmpty());
        assertEquals(List.of(SPEED), state.tick(c, effect(297, false, false), POTIONS));
        assertTrue(state.tick(config(0, 10), effect(1, false, false), POTIONS).isEmpty());
    }

    @Test void graceLastsExactlyConfiguredTicksAndInventoryDoesNotRestartIt() {
        var c = config(15, 1);
        state.tick(c, effect(1, false, false), POTIONS);
        assertEquals(List.of(SPEED), state.tick(c, Map.of(), POTIONS));
        assertTrue(state.tick(c, Map.of(), Set.of()).isEmpty());
        for (int i = 0; i < 18; i++) assertEquals(List.of(SPEED), state.tick(c, Map.of(), POTIONS));
        assertTrue(state.tick(c, Map.of(), POTIONS).isEmpty());
        assertTrue(state.tick(c, Map.of(), POTIONS).isEmpty());
    }

    @Test void neverActiveEarlyRemovalAndZeroGraceDoNotPersist() {
        var c = config(15, 10);
        assertTrue(state.tick(c, Map.of(), POTIONS).isEmpty());
        state.tick(c, effect(20, false, false), POTIONS);
        assertTrue(state.tick(c, Map.of(), POTIONS).isEmpty());
        state.tick(config(15, 0), effect(1, false, false), POTIONS);
        assertTrue(state.tick(config(15, 0), Map.of(), POTIONS).isEmpty());
    }

    @Test void ambientEffectsNeverWarnOrLeaveGraceAndClearOldGrace() {
        var c = config(15, 10);
        assertTrue(state.tick(c, effect(1, true, false), POTIONS).isEmpty());
        assertTrue(state.tick(c, Map.of(), POTIONS).isEmpty());
        state.tick(c, effect(1, false, false), POTIONS);
        assertTrue(state.tick(c, effect(1, true, false), POTIONS).isEmpty());
        assertTrue(state.tick(c, Map.of(), POTIONS).isEmpty());
        state.tick(c, effect(1, false, false), POTIONS);
        assertFalse(state.tick(c, Map.of(), POTIONS).isEmpty());
        assertTrue(state.tick(c, effect(1, true, false), POTIONS).isEmpty());
        assertTrue(state.tick(c, Map.of(), POTIONS).isEmpty());
        assertEquals(List.of(SPEED), state.tick(c, effect(100, false, false), POTIONS));
    }

    @Test void infiniteAndReapplicationCancelGrace() {
        var c = config(15, 10);
        state.tick(c, effect(1, false, false), POTIONS);
        state.tick(c, Map.of(), POTIONS);
        assertTrue(state.tick(c, effect(600, false, false), POTIONS).isEmpty());
        assertTrue(state.tick(c, Map.of(), POTIONS).isEmpty());
        assertTrue(state.tick(c, effect(-1, false, true), POTIONS).isEmpty());
        assertTrue(state.tick(c, Map.of(), POTIONS).isEmpty());
    }

    @Test void orderRemovalAndReset() {
        var c = config(15, 10);
        var active = Map.of(SPEED, new RepotState.Effect(1,false,false), STRENGTH, new RepotState.Effect(1,false,false));
        assertEquals(List.of(SPEED, STRENGTH), state.tick(c, active, POTIONS));
        state.clear(); assertTrue(state.tick(c, Map.of(), POTIONS).isEmpty());
        state.tick(c, active, POTIONS);
        state.tick(new RepotConfig(false,c.effects(),15,10,true,4,c.template()), Map.of(), POTIONS);
        assertTrue(state.tick(c, Map.of(), POTIONS).isEmpty());
        state.tick(c, active, POTIONS);
        state.tick(new RepotConfig(true,List.of(),15,10,true,4,c.template()), Map.of(), POTIONS);
        assertTrue(state.tick(c, Map.of(), POTIONS).isEmpty());
    }

    @Test void defaultsBoundsAndTemplateValidation() {
        var defaults = RepotConfig.defaults();
        assertEquals(List.of(SPEED, STRENGTH, "minecraft:fire_resistance", "minecraft:weaving"), defaults.effects());
        assertEquals("Repot Speed, Strength", defaults.format(List.of("Speed", "Strength")));
        var bounded = new RepotConfig(true,List.of(SPEED,SPEED,"bad id"),-1,700,true,100,"{effect}").validated();
        assertEquals(List.of(SPEED), bounded.effects()); assertEquals(0,bounded.thresholdSeconds());
        assertEquals(600,bounded.graceSeconds()); assertEquals(20,bounded.iconSpacing());
        for (String text : List.of("", "Repot", "{effect} {wrong}", "{effect}}"))
            assertFalse(RepotConfig.textError(text).isEmpty());
    }

    @TempDir Path directory;
    @Test void legacyAndPartialConfigurationMigrateAndCustomSettingsPersist() throws Exception {
        var path = directory.resolve("survival.json");
        Files.writeString(path,"{\"retotemText\":\"Custom\"}");
        var store = new SurvivalConfigStore(path); store.load();
        assertEquals(RepotConfig.defaults(), store.current().repot());
        Files.writeString(path,"{\"retotemText\":\"Custom\",\"repot\":{\"effects\":[],\"icons\":false,\"graceSeconds\":25}}");
        store.load(); store.save(store.current());
        var loaded = new SurvivalConfigStore(path); loaded.load();
        assertEquals(store.current(),loaded.current()); assertEquals("Custom",loaded.current().retotemText());
        assertTrue(loaded.current().repot().effects().isEmpty()); assertFalse(loaded.current().repot().icons());
        assertEquals(15,loaded.current().repot().thresholdSeconds()); assertEquals(25,loaded.current().repot().graceSeconds());
    }

    @Test void legacyProfilesAndCustomClipboardRoundTrip() {
        var gson = new Gson();
        var root = gson.toJsonTree(ProfileSettings.defaults()).getAsJsonObject();
        root.getAsJsonObject("survival").remove("repot"); root.getAsJsonObject("hud").remove("repot");
        var migrated = gson.fromJson(root,ProfileSettings.class).validated();
        assertEquals(RepotConfig.defaults(),migrated.survival().repot()); assertEquals(HudConfig.repotDefault(),migrated.hud().repot());
        root.getAsJsonObject("survival").add("repot",gson.toJsonTree(config(35,22)));
        var style = HudConfig.repotDefault().edit(25,30,2,0x123456,0,0,100,1,.45);
        root.getAsJsonObject("hud").add("repot",gson.toJsonTree(style));
        var custom = gson.fromJson(root,ProfileSettings.class).validated();
        var profile = new SettingsProfile(UUID.randomUUID().toString(),"Repot",List.of(),custom);
        assertEquals(custom,ProfileClipboard.decode(ProfileClipboard.encode(profile)).settings());
        assertEquals(custom,ProfileClipboard.decode(ProfileClipboard.encodeAscii(profile)).settings());
        assertEquals(style,custom.hud().with(0,custom.hud().pitch()).repot());
    }

    @Test void moduleTogglePreservesCustomRepotSettings() throws Exception {
        var gson = new Gson();
        var root = gson.toJsonTree(ProfileSettings.defaults()).getAsJsonObject();
        root.getAsJsonObject("survival").add("repot",gson.toJsonTree(config(45,30)));
        var manager = new ProfileManager(directory.resolve("profiles.json"), ignored -> {});
        manager.load(gson.fromJson(root,ProfileSettings.class).validated());
        manager.setModuleEnabled(ProfileManager.Module.SURVIVAL,false);
        manager.setModuleEnabled(ProfileManager.Module.SURVIVAL,true);
        assertEquals(config(45,30),manager.active().settings().survival().repot());
    }

    @Test void malformedNestedSettingsAreBackedUp() throws Exception {
        var path = directory.resolve("survival.json");
        Files.writeString(path,"{\"repot\":{\"effects\":[false]}}");
        var store = new SurvivalConfigStore(path); store.load();
        assertEquals(SurvivalConfig.defaults(),store.current());
        try (var files = Files.list(directory)) { assertEquals(2,files.count()); }
    }
}
