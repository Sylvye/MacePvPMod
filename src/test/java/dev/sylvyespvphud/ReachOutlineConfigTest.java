package dev.sylvyespvphud;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
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
        assertEquals(OutlineRenderMode.WIREFRAME,c.renderMode());
        var l=c.liquidSources();assertTrue(l.enabled());assertTrue(l.water());assertTrue(l.lava());assertEquals(8,l.maximumSources());
        assertEquals(c.renderMode(),l.renderMode());assertEquals(c.color(),l.color());assertEquals(c.intensity(),l.intensity());
        assertEquals(c.thickness(),l.thickness());assertEquals(c.hardToReachMode(),l.hardToReachMode());
    }
    @Test void buildsArgbColor() { assertEquals(0x8066ccff,new ReachOutlineConfig(1,true,0x66ccff,.5,1,false,HardToReachMode.RENDER_FULL,.25).argb()); }
    @Test void loadsLegacyConfigurationWithNewDefaults() throws Exception {
        Path path=directory.resolve("reach.json");
        Files.writeString(path,"{\"schemaVersion\":1,\"enabled\":true,\"color\":6737151,\"intensity\":0.4,\"thickness\":3}");
        var store=new ReachOutlineConfigStore(path);store.load();var c=store.current();
        assertFalse(c.topFacesOnly());assertEquals(HardToReachMode.FADE_FACES,c.hardToReachMode());assertEquals(.5,c.minimumReachableArea());
        assertEquals(3,c.thickness());assertEquals(.4,c.intensity());
        assertEquals(OutlineRenderMode.WIREFRAME,c.renderMode());assertEquals(LiquidSourceConfig.defaults(),c.liquidSources());
    }

    @Test void validatesLiquidLimitsAndIndependentVisuals() {
        var c=new LiquidSourceConfig(false,false,true,999,null,-1,Double.NaN,0,true,null,Double.NaN).validated();
        assertFalse(c.enabled());assertFalse(c.water());assertTrue(c.lava());assertEquals(64,c.maximumSources());
        assertEquals(OutlineRenderMode.WIREFRAME,c.renderMode());assertEquals(0,c.color());assertEquals(.22,c.intensity());
        assertEquals(1,c.thickness());assertTrue(c.topFacesOnly());assertEquals(.5,c.minimumReachableArea());
        assertEquals(HardToReachMode.FADE_FACES,c.hardToReachMode());
        var min=new LiquidSourceConfig(true,true,true,-5,OutlineRenderMode.TINT,0xffffff,5,50,false,HardToReachMode.RENDER_FULL,.001).validated();
        assertEquals(1,min.maximumSources());assertEquals(1,min.intensity());assertEquals(10,min.thickness());assertEquals(.01,min.minimumReachableArea());
    }

    @Test void modesAndLiquidChoicesRoundTripAndSurviveMasterToggle() throws Exception {
        var l=new LiquidSourceConfig(false,false,true,17,OutlineRenderMode.TINT,0xff7700,.5,8,true,HardToReachMode.DO_NOT_RENDER,.75);
        var c=new ReachOutlineConfig(1,true,0x44ff22,.3,4,false,HardToReachMode.RENDER_FULL,.25,OutlineRenderMode.TINT,l);
        var store=new ReachOutlineConfigStore(directory.resolve("reach.json"));store.save(c);
        var reloaded=new ReachOutlineConfigStore(directory.resolve("reach.json"));reloaded.load();assertEquals(c,reloaded.current());
        assertEquals(c,c.withEnabled(false).withEnabled(true));
        assertEquals(0x80ff7700,l.argb());assertEquals(OutlineRenderMode.WIREFRAME,c.renderMode().next());
    }

    @Test void unknownOrMissingModesFallBackWithoutResettingOldFields() {
        var c=new Gson().fromJson("{\"schemaVersion\":1,\"enabled\":false,\"color\":123,\"intensity\":0.6,\"thickness\":7,\"renderMode\":\"FUTURE\"}",ReachOutlineConfig.class).validated();
        assertEquals(OutlineRenderMode.WIREFRAME,c.renderMode());assertFalse(c.enabled());assertEquals(123,c.color());
        assertEquals(.6,c.intensity());assertEquals(7,c.thickness());assertEquals(LiquidSourceConfig.defaults(),c.liquidSources());
    }
}
