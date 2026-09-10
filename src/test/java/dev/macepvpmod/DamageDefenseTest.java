package dev.macepvpmod;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageDefenseTest {
    @Test void armorToughnessBreachAndProtectionFollowVanillaOrder() {
        assertEquals(10, MaceDamageCalculator.afterGear(10,0,0,0,0),1e-6);
        assertEquals(4, MaceDamageCalculator.afterGear(10,20,0,0,0),1e-6);
        assertEquals(10, MaceDamageCalculator.afterGear(10,20,0,4,0),1e-6);
        assertEquals(2.4, MaceDamageCalculator.afterGear(10,20,0,0,10),1e-6);
        assertEquals(2, MaceDamageCalculator.afterGear(10,0,0,0,20),1e-6);
    }
}
