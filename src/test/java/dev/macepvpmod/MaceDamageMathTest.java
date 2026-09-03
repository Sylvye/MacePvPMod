package dev.macepvpmod;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaceDamageMathTest {
    private double hit(double fall, int density) { return MaceDamageMath.calculate(6, 1, fall, false, density, false); }
    @Test void vanillaSmashTiersIncludeFractionalBlocks() {
        assertEquals(6, hit(1.5, 0));
        assertEquals(12.04, hit(1.51, 0), 1e-6);
        assertEquals(18, hit(3, 0));
        assertEquals(19, hit(3.5, 0));
        assertEquals(28, hit(8, 0));
        assertEquals(28.5, hit(8.5, 0));
        assertEquals(120, hit(100, 0));
    }
    @Test void densityAddsPerLevelPerBlockOnlyOnSmashes() {
        assertEquals(6, hit(1.5, 5));
        for (int level = 1; level <= 5; level++) {
            assertEquals(28 + 4 * level, hit(8, level));
            assertEquals(120 + 50 * level, hit(100, level));
        }
    }
    @Test void cooldownScalesBaseButNotSmashBonus() {
        assertEquals(1.2, MaceDamageMath.calculate(6, 0, 0, false, 0, false), 1e-6);
        assertEquals(24.4, MaceDamageMath.calculate(6, .5, 8, false, 0, true), 1e-6);
    }
    @Test void glidingDisablesSmashAndCriticalScalesWholeHit() {
        assertEquals(6, MaceDamageMath.calculate(6, 1, 100, true, 5, false));
        assertEquals(72, MaceDamageMath.calculate(6, 1, 8, false, 5, true));
        assertEquals(9, MaceDamageMath.calculate(6, 1, 1, false, 5, true));
    }
}
