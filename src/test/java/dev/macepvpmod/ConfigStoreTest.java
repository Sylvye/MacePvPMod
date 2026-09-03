package dev.macepvpmod;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ConfigStoreTest {
    @TempDir Path directory;
    @Test void missingFileUsesDefaults() {
        var store = new ConfigStore(directory.resolve("config.json"));
        store.load(); assertEquals(PitchConfig.defaults(), store.current());
    }
    @Test void saveSurvivesReload() throws Exception {
        Path file = directory.resolve("config.json");
        var c = new PitchConfig(1, false, 150, 3, 0xffffff, .8, 35, 4, 90, true);
        new ConfigStore(file).save(c);
        var store = new ConfigStore(file); store.load(); assertEquals(c, store.current());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void damagedConfigIsPreservedAndRecoverable() throws Exception {
        Path file = directory.resolve("config.json"); Files.writeString(file, "{broken");
        var store = new ConfigStore(file); store.load();
        assertEquals(PitchConfig.defaults(), store.current());
        try (var files = Files.list(directory)) {
            Path backup = files.filter(p -> p.getFileName().toString().startsWith("macepvpmod-invalid-")).findFirst().orElseThrow();
            assertEquals("{broken", Files.readString(backup));
        }
        store.save(store.current());
        var reloaded = new ConfigStore(file); reloaded.load(); assertEquals(store.current(), reloaded.current());
    }
    @Test void missingFieldsUseDefaultsAndBoundsAreClamped() throws Exception {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "{\"width\":9999,\"targetPitch\":-100,\"enabled\":false}");
        var store = new ConfigStore(file); store.load();
        assertEquals(400, store.current().width()); assertEquals(-90, store.current().targetPitch());
        assertEquals(2, store.current().sensitivity()); assertFalse(store.current().enabled());
    }
    @Test void invalidTypesAndFutureVersionRecover() throws Exception {
        for (String json : new String[]{"null", "[]", "{\"enabled\":\"true\"}", "{\"width\":null}", "{\"schemaVersion\":2}"}) {
            Path file = directory.resolve("config.json"); Files.writeString(file, json);
            var store = new ConfigStore(file); store.load(); assertEquals(PitchConfig.defaults(), store.current());
        }
    }
    @Test void failedSaveDoesNotChangeActiveConfiguration() throws Exception {
        Path file = directory.resolve("directory.json"); Files.createDirectory(file); Files.writeString(file.resolve("keep"), "data");
        var store = new ConfigStore(file);
        var changed = new PitchConfig(1, false, 100, 1, 0, .5, 40, 2, 60, false);
        assertThrows(java.io.IOException.class, () -> store.save(changed));
        assertEquals(PitchConfig.defaults(), store.current());
    }
}
