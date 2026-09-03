package dev.macepvpmod;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PitchMathTest {
    private final PitchConfig config = PitchConfig.defaults();
    @Test void alignmentAndDirection() {
        assertEquals(0, PitchMath.offset(40, config));
        assertEquals(20, PitchMath.offset(30, config));
        assertEquals(-20, PitchMath.offset(50, config));
    }
    @Test void extremePitchIsClamped() {
        assertEquals(60, PitchMath.offset(-90, config));
        assertEquals(-60, PitchMath.offset(90, config));
        assertEquals(0, PitchMath.offset(Double.NaN, config));
    }
    @Test void customTargetAndSensitivity() {
        var c = new PitchConfig(1, true, 100, 1, 0x999999, .4, -20, 3, 25, false);
        assertEquals(15, PitchMath.offset(-25, c));
        assertEquals(-25, PitchMath.offset(90, c));
    }
}
