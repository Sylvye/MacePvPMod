package dev.macepvpmod;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SurvivalTest {
    @TempDir Path directory;
    @Test void retotemRequiresAvailableTotemAndEmptyOffhand() {
        assertTrue(SurvivalState.needsRetotem(true, true));
        assertFalse(SurvivalState.needsRetotem(false, true));
        assertFalse(SurvivalState.needsRetotem(true, false));
        assertFalse(SurvivalState.needsRetotem(false, false));
    }
    @Test void healingStatesAndInclusiveHalfThresholds() {
        var c = SurvivalConfig.defaults();
        assertEquals(0, SurvivalState.healingState(10.1, 20, 10.1, c));
        assertEquals(1, SurvivalState.healingState(10, 20, 11, c));
        assertEquals(2, SurvivalState.healingState(20, 20, 10, c));
        assertEquals(3, SurvivalState.healingState(10, 20, 10, c));
        assertEquals(1, SurvivalState.healingState(20, 40, 11, c));
    }
    @Test void customSettingsPersistAndThresholdsApply() throws Exception {
        var file = directory.resolve("survival.json");
        Files.writeString(file, "{\"healthPercent\":25,\"saturationThreshold\":3,\"retotemEnabled\":false,\"combinedText\":\"Heal now\",\"healingX\":72,\"healthColor\":1193046}");
        var store = new SurvivalConfigStore(file); store.load();
        var c = store.current();
        assertFalse(c.retotemEnabled()); assertTrue(c.healingEnabled());
        assertEquals("Heal now", c.combinedText()); assertEquals(72, c.healingX());
        assertEquals(0x123456, c.healthColor());
        assertEquals(0, SurvivalState.healingState(6, 20, 4, c));
        assertEquals(3, SurvivalState.healingState(5, 20, 3, c));
        store.save(c);
        var loaded = new SurvivalConfigStore(file); loaded.load();
        assertEquals(c, loaded.current());
    }
    @Test void volumeDefaultsForExistingConfigsAndPersists() throws Exception {
        var file = directory.resolve("survival.json");
        Files.writeString(file, "{\"healthPercent\":25}");
        var store = new SurvivalConfigStore(file); store.load();
        assertEquals(.5, store.current().harpVolume());
        Files.writeString(file, "{\"lowHealthVolume\":0.23}");
        store.load(); store.save(store.current());
        var loaded = new SurvivalConfigStore(file); loaded.load();
        assertEquals(.23, loaded.current().harpVolume());
        Files.writeString(file, "{\"lowHealthVolume\":2}");
        loaded.load(); assertEquals(1, loaded.current().harpVolume());
        Files.writeString(file, "{\"lowHealthVolume\":-1}");
        loaded.load(); assertEquals(0, loaded.current().harpVolume());
    }
    @Test void independentSoundSettingsPersistAndClamp() throws Exception {
        var file = directory.resolve("survival.json");
        Files.writeString(file, "{\"harpVolume\":0.2,\"bassVolume\":0.8,\"harpPitch\":0.75,\"bassPitch\":1.5}");
        var store = new SurvivalConfigStore(file); store.load(); store.save(store.current());
        var loaded = new SurvivalConfigStore(file); loaded.load();
        assertEquals(.2, loaded.current().harpVolume()); assertEquals(.8, loaded.current().bassVolume());
        assertEquals(.75, loaded.current().harpPitch()); assertEquals(1.5, loaded.current().bassPitch());
        Files.writeString(file, "{\"lowHealthVolume\":0.3,\"harpVolume\":0.9,\"harpPitch\":0,\"bassPitch\":5}");
        loaded.load();
        assertEquals(.9, loaded.current().harpVolume()); assertEquals(.3, loaded.current().bassVolume());
        assertEquals(.5, loaded.current().harpPitch()); assertEquals(2, loaded.current().bassPitch());
    }
    @Test void audioIntervalsDefaultPersistAndValidate() throws Exception {
        var file = directory.resolve("survival.json");
        Files.writeString(file, "{}");
        var store = new SurvivalConfigStore(file); store.load();
        assertEquals(.75, store.current().audioStartInterval());
        assertEquals(.25, store.current().audioEndInterval());
        Files.writeString(file, "{\"audioStartInterval\":1.5,\"audioEndInterval\":0.5}");
        store.load(); store.save(store.current());
        var loaded = new SurvivalConfigStore(file); loaded.load();
        assertEquals(1.5, loaded.current().audioStartInterval());
        assertEquals(.5, loaded.current().audioEndInterval());
        Files.writeString(file, "{\"audioStartInterval\":0,\"audioEndInterval\":5}");
        loaded.load();
        assertEquals(.05, loaded.current().audioStartInterval());
        assertEquals(.05, loaded.current().audioEndInterval());
    }
    @Test void malformedConfigIsBackedUp() throws Exception {
        var file = directory.resolve("survival.json");
        Files.writeString(file, "{\"retotemText\": false}");
        var store = new SurvivalConfigStore(file); store.load();
        assertEquals(SurvivalConfig.defaults(), store.current());
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }
}
