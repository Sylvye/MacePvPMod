package dev.macepvpmod;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SurvivalItemsTest {
    @TempDir Path directory;
    @Test void fullEligibilityMatrix() {
        int[][] expected = {{0,0,0,0}, {0,1,0,1}, {0,1,2,3}, {0,1,2,3}};
        for (int inventory = 0; inventory < 4; inventory++)
            for (int state = 0; state < 4; state++)
                assertEquals(expected[inventory][state], SurvivalState.actionableState(state,
                        (inventory & 1) != 0, (inventory & 2) != 0));
    }
    @Test void defaultsCoverEveryPotionFormAndRequestedFood() {
        assertEquals(15, SurvivalItemRule.healingDefaults().size());
        assertEquals(7, SurvivalItemRule.saturationDefaults().size());
        assertTrue(SurvivalItemRule.healingDefaults().contains(new SurvivalItemRule("minecraft:lingering_potion", "minecraft:strong_regeneration")));
        assertFalse(SurvivalItemRule.healingDefaults().contains(new SurvivalItemRule("minecraft:potion", "minecraft:poison")));
    }
    @Test void legacyDefaultsEmptyListsDuplicatesAndCustomRulesPersist() throws Exception {
        var path = directory.resolve("survival.json");
        var store = new SurvivalConfigStore(path);
        Files.writeString(path, "{}"); store.load();
        assertEquals(SurvivalItemRule.healingDefaults(), store.current().healingItems());
        Files.writeString(path, """
                {"healingItems":[],"saturationItems":[{"item":"example:food"},{"item":"example:food"},{"item":"minecraft:splash_potion","potion":"minecraft:healing"}]}
                """);
        store.load(); store.save(store.current());
        var loaded = new SurvivalConfigStore(path); loaded.load();
        assertTrue(loaded.current().healingItems().isEmpty());
        assertEquals(2, loaded.current().saturationItems().size());
        assertEquals(store.current(), loaded.current());
    }
    @Test void invalidListIsBackedUp() throws Exception {
        var path = directory.resolve("survival.json");
        Files.writeString(path, "{\"healingItems\":[{\"item\":false}]}");
        var store = new SurvivalConfigStore(path); store.load();
        assertEquals(SurvivalConfig.defaults(), store.current());
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }
}
