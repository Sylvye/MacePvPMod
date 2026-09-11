package dev.macepvpmod;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SettingsWorkspaceTest {
    @TempDir Path directory;

    @Test void oldCompoundConfigsMigrateEnabled() throws Exception {
        Path damageFile=directory.resolve("damage.json");Files.writeString(damageFile,"{\"schemaVersion\":1,\"fallEnabled\":false}");
        var damage=new DamageConfigStore(damageFile);damage.load();assertEquals(2,damage.current().schemaVersion());assertTrue(damage.current().enabled());assertFalse(damage.current().fallEnabled());
        Path survivalFile=directory.resolve("survival.json");Files.writeString(survivalFile,"{\"schemaVersion\":1,\"healingEnabled\":false}");
        var survival=new SurvivalConfigStore(survivalFile);survival.load();assertEquals(2,survival.current().schemaVersion());assertTrue(survival.current().enabled());assertFalse(survival.current().healingEnabled());
        Path swapFile=directory.resolve("swap.json");Files.writeString(swapFile,"{\"schemaVersion\":1,\"visualEnabled\":false}");
        var swap=new AttributeSwapConfigStore(swapFile);swap.load();assertEquals(2,swap.current().schemaVersion());assertTrue(swap.current().enabled());assertFalse(swap.current().visualEnabled());
    }

    @Test void masterSwitchPreservesFeatureChoices() {
        var d=DamageConfig.defaults();
        var off=new DamageConfig(2,false,d.fallColor(),d.fallSize(),d.fallX(),d.fallY(),true,d.hitColor(),d.hitSize(),d.hitX(),d.hitY(),d.hitSeconds(),d.calculatedDamage(),d.fallTemplate(),d.hitTemplate(),d.fallThreshold(),d.fallColors(),d.hitColors(),d.maceEnabled(),d.spearEnabled(),d.swordAxeEnabled(),d.useEnemyGear(),d.boldCriticalDamage(),false).validated();
        assertFalse(off.enabled());assertFalse(off.fallEnabled());assertTrue(off.hitEnabled());
    }
}
