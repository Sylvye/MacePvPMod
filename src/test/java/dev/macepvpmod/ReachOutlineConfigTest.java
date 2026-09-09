package dev.macepvpmod;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReachOutlineConfigTest {
    @TempDir Path directory;

    @Test void clampsVisualSettings() {
        var c=new ReachOutlineConfig(1,true,-1,Double.NaN,99,true,null,0).validated();
        assertEquals(0,c.color()); assertEquals(.22,c.intensity()); assertEquals(ReachOutlineConfig.MAX_THICKNESS,c.thickness());
        assertTrue(c.topFacesOnly()); assertEquals(HardToReachMode.FADE_FACES,c.hardToReachMode()); assertEquals(.5,c.minimumReachableArea());
    }
    @Test void defaultsToRequestedModes() {
        var c=ReachOutlineConfig.defaults();
        assertEquals(5,c.thickness()); assertFalse(c.topFacesOnly());
        assertEquals(HardToReachMode.FADE_FACES,c.hardToReachMode()); assertEquals(.5,c.minimumReachableArea());
    }
    @Test void buildsArgbColor() { assertEquals(0x8066ccff,new ReachOutlineConfig(1,true,0x66ccff,.5,1,false,HardToReachMode.RENDER_FULL,.25).argb()); }
    @Test void loadsLegacyConfigurationWithNewDefaults() throws Exception {
        Path path=directory.resolve("reach.json");
        Files.writeString(path,"{\"schemaVersion\":1,\"enabled\":true,\"color\":6737151,\"intensity\":0.4,\"thickness\":3}");
        var store=new ReachOutlineConfigStore(path);store.load();var c=store.current();
        assertFalse(c.topFacesOnly());assertEquals(HardToReachMode.FADE_FACES,c.hardToReachMode());assertEquals(.5,c.minimumReachableArea());
        assertEquals(3,c.thickness());assertEquals(.4,c.intensity());
    }
}
