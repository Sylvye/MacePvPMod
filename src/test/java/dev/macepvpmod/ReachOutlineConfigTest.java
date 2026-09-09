package dev.macepvpmod;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class ReachOutlineConfigTest {
    @Test void clampsVisualSettings() {
        var c=new ReachOutlineConfig(1,true,-1,Double.NaN,99).validated();
        assertEquals(0,c.color()); assertEquals(.22,c.intensity()); assertEquals(ReachOutlineConfig.MAX_THICKNESS,c.thickness());
    }
    @Test void defaultsToComfortableThickness() { assertEquals(5,ReachOutlineConfig.defaults().thickness()); }
    @Test void buildsArgbColor() { assertEquals(0x8066ccff,new ReachOutlineConfig(1,true,0x66ccff,.5,1).argb()); }
}
