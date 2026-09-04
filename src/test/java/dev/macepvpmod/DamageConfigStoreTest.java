package dev.macepvpmod;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DamageConfigStoreTest {
    @TempDir Path directory;
    @Test void independentFeaturesRoundTrip() throws Exception {
        var file = directory.resolve("damage.json");
        var config = new DamageConfig(1, false, 0x112233, .7, -70, 40, true, 0xabcdef, 2, 50, -90, 6, true);
        new DamageConfigStore(file).save(config);
        var loaded = new DamageConfigStore(file); loaded.load();
        assertEquals(config, loaded.current());
    }
    @Test void newFieldsDefaultAndInvalidValuesClamp() throws Exception {
        var file = directory.resolve("damage.json");
        Files.writeString(file, "{\"fallEnabled\":false,\"hitSize\":999,\"hitSeconds\":0}");
        var store = new DamageConfigStore(file); store.load();
        assertFalse(store.current().calculatedDamage());
        assertFalse(store.current().fallEnabled()); assertTrue(store.current().hitEnabled());
        assertEquals(14, store.current().fallY()); assertEquals(4, store.current().hitSize()); assertEquals(1, store.current().hitSeconds());
        assertEquals(1.5, store.current().fallThreshold());
    }

    @Test void thresholdPersistsAndIsBounded() throws Exception {
        var file = directory.resolve("damage.json");
        var config = new DamageConfig(1, true, 0xffffff, 1, 0, 14, true, 0xff6666, 1, 0, 28, 3, false,
                "{blocks} blocks", "{damage} damage", 7.25);
        new DamageConfigStore(file).save(config);
        var loaded = new DamageConfigStore(file); loaded.load();
        assertEquals(7.25, loaded.current().fallThreshold());
        Files.writeString(file, "{\"fallThreshold\":999}"); loaded.load();
        assertEquals(100, loaded.current().fallThreshold());
    }

    @Test void thresholdControlsFallVisibilityAndIsClamped() {
        assertTrue(DamageHud.showFall(2, 1.5));
        assertFalse(DamageHud.showFall(1.5, 1.5));
        assertFalse(DamageHud.showFall(1, 1.5));
        assertEquals(0, new DamageConfig(1, true, 0xffffff, 1, 0, 14, true, 0xff6666, 1, 0, 28, 3,
                false, "{blocks} blocks", "{damage} damage", -4).validated().fallThreshold());
        assertEquals(100, new DamageConfig(1, true, 0xffffff, 1, 0, 14, true, 0xff6666, 1, 0, 28, 3,
                false, "{blocks} blocks", "{damage} damage", 200).validated().fallThreshold());
    }
}
