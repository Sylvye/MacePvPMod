package dev.sylvyespvphud;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileManagerTest {
    @TempDir Path directory;

    @Test void firstLoadMigratesLegacyAndRoundTripsCatalog() throws Exception {
        var active = new AtomicReference<ProfileSettings>();
        var legacy = ProfileSettings.defaults();
        var manager = new ProfileManager(directory.resolve("profiles.json"), active::set);
        manager.load(legacy);
        assertEquals("Default", manager.active().name());
        assertEquals(legacy, active.get());
        assertTrue(Files.readString(directory.resolve("profiles.json")).contains("baselineProfileId"));
        var reloaded = new ProfileManager(directory.resolve("profiles.json"), active::set);
        reloaded.load(ProfileSettings.defaults());
        assertEquals(manager.profiles(), reloaded.profiles());
    }

    @Test void existingCatalogMigratesTrackerWithoutResettingOtherSettings() throws Exception {
        Path path=directory.resolve("profiles.json");
        var manager=new ProfileManager(path,ignored->{});manager.load(ProfileSettings.defaults());
        var root=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        var tracker=root.getAsJsonArray("profiles").get(0).getAsJsonObject().getAsJsonObject("settings").getAsJsonObject("playerTracker");
        tracker.addProperty("schemaVersion",3);tracker.addProperty("radius",84);tracker.addProperty("hideDistantPlayers",true);
        tracker.remove("placement");tracker.remove("nearFadeDistance");
        Files.writeString(path,root.toString());
        var reloaded=new ProfileManager(path,ignored->{});reloaded.load(ProfileSettings.defaults());
        var migrated=reloaded.active().settings().playerTracker();
        assertEquals(4,migrated.schemaVersion());assertEquals(84,migrated.radius());assertTrue(migrated.hideDistantPlayers());
        assertEquals(TrackerPlacement.RING,migrated.placement());assertEquals(20,migrated.nearFadeDistance());
    }

    @Test void matchingOverrideAutoAndDisconnectFollowSelectionRules() throws Exception {
        var active = new AtomicReference<ProfileSettings>();
        var manager = new ProfileManager(directory.resolve("profiles.json"), active::set);
        manager.load(ProfileSettings.defaults());
        SettingsProfile pvp = manager.create("PvP", false);
        manager.assignServer(pvp.id(), "PLAY.Example.COM.");
        manager.join("play.example.com:25565");
        assertEquals("PvP", manager.active().name());
        manager.switchManual("Default");
        assertTrue(manager.manualOverride()); assertEquals("Default", manager.active().name());
        manager.useAutomatic();
        assertFalse(manager.manualOverride()); assertEquals("PvP", manager.active().name());
        manager.disconnect();
        assertEquals("Default", manager.active().name());
    }

    @Test void unmatchedServerUsesBaselineAndAssignmentsHaveOneOwner() throws Exception {
        var manager = new ProfileManager(directory.resolve("profiles.json"), ignored -> {});
        manager.load(ProfileSettings.defaults());
        SettingsProfile first = manager.create("First", false), second = manager.create("Second", false);
        manager.switchManual(first.id());
        manager.join("unmatched.test"); assertEquals("First", manager.active().name());
        manager.assignServer(first.id(), "server.test");
        manager.assignServer(second.id(), "SERVER.TEST:25565");
        assertFalse(manager.profiles().stream().filter(p -> p.id().equals(first.id())).findFirst().orElseThrow().servers().contains("server.test:25565"));
        assertEquals(second.id(), manager.ownerOfServer("server.test").id());
    }

    @Test void clipboardRoundTripExcludesServersAndImportRenamesCollision() throws Exception {
        var manager = new ProfileManager(directory.resolve("profiles.json"), ignored -> {});
        manager.load(ProfileSettings.defaults());
        manager.assignServer(manager.active().id(), "private.example:25570");
        String shared = manager.exportProfile(manager.active().id());
        assertTrue(shared.startsWith("SPH2:")); assertFalse(shared.contains("private.example"));
        SettingsProfile imported = manager.importProfile(shared);
        assertEquals("Default (2)", imported.name()); assertTrue(imported.servers().isEmpty());
        assertEquals(manager.baseline().settings(), imported.settings());
        assertThrows(IllegalArgumentException.class, () -> ProfileClipboard.decode("SPH2:broken"));
    }

    @Test void normalizesAddressesAndRejectsWildcardsAndBadPorts() {
        assertEquals("example.com:25565", ProfileManager.normalizeServer(" Example.COM. "));
        assertEquals("example.com:25570", ProfileManager.normalizeServer("example.com:25570"));
        assertEquals("[2001:db8::1]:25565", ProfileManager.normalizeServer("2001:DB8::1"));
        assertThrows(IllegalArgumentException.class, () -> ProfileManager.normalizeServer("*.example.com"));
        assertThrows(IllegalArgumentException.class, () -> ProfileManager.normalizeServer("example.com:70000"));
    }

    @Test void damagedCatalogIsBackedUpAndRecovered() throws Exception {
        Path path = directory.resolve("profiles.json"); Files.writeString(path, "{broken");
        var manager = new ProfileManager(path, ignored -> {}); manager.load(ProfileSettings.defaults());
        assertEquals("Default", manager.active().name());
        try (var files = Files.list(directory)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("sylvyespvphud-profiles-invalid-")));
        }
    }

    @Test void moduleSettingsCanBeReadChangedPersistedAndActivated() throws Exception {
        var active = new AtomicReference<ProfileSettings>();
        Path path = directory.resolve("profiles.json");
        var manager = new ProfileManager(path, active::set); manager.load(ProfileSettings.defaults());
        assertTrue(manager.moduleEnabled(ProfileManager.Module.VECTORS) == manager.active().settings().vectors().enabled());
        manager.setModuleEnabled(ProfileManager.Module.VECTORS, true);
        assertTrue(manager.moduleEnabled(ProfileManager.Module.VECTORS));
        assertTrue(active.get().vectors().enabled());
        var reloaded = new ProfileManager(path, ignored -> {}); reloaded.load(ProfileSettings.defaults());
        assertTrue(reloaded.moduleEnabled(ProfileManager.Module.VECTORS));
    }

    @Test void failedAtomicSaveLeavesSelectionAndLiveSettingsUnchanged() throws Exception {
        Path path = directory.resolve("profiles.json");
        var active = new AtomicReference<ProfileSettings>();
        var manager = new ProfileManager(path, active::set); manager.load(ProfileSettings.defaults());
        manager.create("Second", false);
        ProfileSettings before = active.get();
        Files.delete(path); Files.createDirectory(path);
        assertThrows(java.io.IOException.class, () -> manager.switchManual("Second"));
        assertEquals("Default", manager.active().name()); assertEquals(before, active.get());
    }
}
