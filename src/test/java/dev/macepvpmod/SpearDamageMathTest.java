package dev.macepvpmod;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class SpearDamageMathTest {
    @Test void relativeSpeedUsesBothProjectedVectors(){
        assertEquals(10,SpearDamageMath.relative(10,0));
        assertEquals(6,SpearDamageMath.relative(10,4));
        assertEquals(14,SpearDamageMath.relative(10,-4));
        assertEquals(0,SpearDamageMath.relative(4,10));
    }
    @Test void rawDamageUsesBaseAndFlooredVelocityBonus(){
        assertEquals(5,SpearDamageMath.raw(1,10.9,.4));
        assertEquals(1,SpearDamageMath.raw(1,-10,.4));
    }
}
