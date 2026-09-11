package dev.macepvpmod;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColorScaleTest {
    @Test void domainOverridePreservesStopsAndOriginalScale() {
        var original=ColorScale.damageDefault();
        var effective=original.withDomain(0,20);
        assertEquals(20,effective.maximum());
        assertEquals(original.keys(),effective.keys());
        assertEquals(80,original.maximum());
    }
    @Test void defaultsHaveExpectedKeysAndEndpoints() {
        var scale=ColorScale.damageDefault().validated();
        assertEquals(0xffffff,scale.color(-1));
        assertEquals(0xffffff,scale.color(0));
        assertEquals(0xffff00,scale.color(20));
        assertEquals(0xffa500,scale.color(40));
        assertEquals(0xff0000,scale.color(80));
        assertEquals(0xff0000,scale.color(999));
    }
    @Test void interpolatesRgbWithinTheCorrectSegment() {
        var scale=ColorScale.damageDefault();
        assertEquals(0xffff80,scale.color(10));
        assertEquals(0xffd200,scale.color(30));
        assertEquals(0xff5300,scale.color(60));
    }
    @Test void mapsRelativeKeysAcrossCustomDomain() {
        var scale=new ColorScale(ColorMode.GRADIENT,0x123456,10,30,
                List.of(new GradientKey(0,0x000000),new GradientKey(.5,0xff0000),new GradientKey(1,0xffffff))).validated();
        assertEquals(0xff0000,scale.color(20));
        assertEquals(0xff8080,scale.color(25));
    }
    @Test void flatModeAndUnavailableValuesUseFlatColor() {
        var scale=ColorScale.flat(0x123456).validated();
        assertEquals(0x123456,scale.color(40));
        assertEquals(0xff0000,ColorScale.damageDefault().color(Double.NaN));
    }
    @Test void rejectsInvalidScales() {
        assertThrows(IllegalArgumentException.class,()->new ColorScale(ColorMode.GRADIENT,0,5,5,ColorScale.damageDefault().keys()).validated());
        assertThrows(IllegalArgumentException.class,()->new ColorScale(ColorMode.GRADIENT,0,0,1,List.of(new GradientKey(0,0),new GradientKey(.5,0xffffff))).validated());
        assertThrows(IllegalArgumentException.class,()->new ColorScale(ColorMode.GRADIENT,0,0,1,List.of(new GradientKey(0,0),new GradientKey(.7,0),new GradientKey(.6,0),new GradientKey(1,0))).validated());
    }
}
