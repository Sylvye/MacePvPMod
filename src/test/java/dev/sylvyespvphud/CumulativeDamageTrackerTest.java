package dev.sylvyespvphud;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CumulativeDamageTrackerTest {
    @AfterEach void clear(){DamageHud.resetTracker();CumulativeDamageTracker.resetAll();}

    @Test void accumulatesPerPlayerAndShowsMostRecent(){
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        CumulativeDamageTracker.record(first,"First",4.25,20,100,false);
        CumulativeDamageTracker.record(second,"Second",3,20,100,false);
        CumulativeDamageTracker.record(first,"First",2,20,100,false);
        assertEquals(6.25,CumulativeDamageTracker.total(first));
        assertEquals(3,CumulativeDamageTracker.total(second));
        assertEquals(first,CumulativeDamageTracker.active());
        assertEquals("First: 6.3 total damage",CumulativeDamageTracker.visible("{player}: {damage} total damage"));
    }

    @Test void refreshesOnlyHitPlayersTimeoutAndExpiresEntries(){
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        CumulativeDamageTracker.record(first,"First",1,2,100,false);
        CumulativeDamageTracker.record(second,"Second",1,3,100,false);
        CumulativeDamageTracker.tick();
        CumulativeDamageTracker.record(first,"First",1,2,100,false);
        CumulativeDamageTracker.tick();
        assertEquals(2,CumulativeDamageTracker.total(first));assertEquals(1,CumulativeDamageTracker.total(second));
        CumulativeDamageTracker.tick();
        assertEquals(0,CumulativeDamageTracker.total(first));assertEquals(0,CumulativeDamageTracker.total(second));assertEquals("2.0",CumulativeDamageTracker.visible("{damage}"));assertEquals(1,CumulativeDamageTracker.opacity());
    }

    @Test void resetAndInvalidDamageAreSafe(){
        UUID id=UUID.randomUUID();
        CumulativeDamageTracker.record(id,"Player",Double.NaN,20,100,false);assertEquals(0,CumulativeDamageTracker.total(id));
        CumulativeDamageTracker.record(id,"Player",5,20,100,false);CumulativeDamageTracker.reset(id);
        assertEquals(0,CumulativeDamageTracker.total(id));assertNull(CumulativeDamageTracker.active());
        assertEquals("Player: 5.0",CumulativeDamageTracker.visible("{player}: {damage}"));
    }

    @Test void templateFormattingAndValidation(){
        assertEquals("Opponent dealt 12.5",CumulativeDamageText.format("{player} dealt {damage}","Opponent",12.45));
        assertEquals("",CumulativeDamageText.error("{player}: {damage}"));
        assertFalse(CumulativeDamageText.error("{unknown}").isEmpty());
        assertFalse(CumulativeDamageText.error(" ").isEmpty());
    }

    @Test void hudLifetimeFadesWithoutClearingStoredDamage(){
        UUID id=UUID.randomUUID();CumulativeDamageTracker.record(id,"Player",5,200,2,false);
        CumulativeDamageTracker.tick();assertEquals(1,CumulativeDamageTracker.opacity());
        CumulativeDamageTracker.tick();assertEquals(1,CumulativeDamageTracker.opacity());assertEquals(5,CumulativeDamageTracker.total(id));
        CumulativeDamageTracker.tick();assertEquals(.95,CumulativeDamageTracker.opacity(),1e-9);
        for(int i=0;i<19;i++)CumulativeDamageTracker.tick();
        assertEquals("",CumulativeDamageTracker.visible("{damage}"));assertEquals(5,CumulativeDamageTracker.total(id));
        CumulativeDamageTracker.record(id,"Player",2,200,2,false);
        assertEquals("7.0",CumulativeDamageTracker.visible("{damage}"));assertEquals(1,CumulativeDamageTracker.opacity());
    }

    @Test void newHitInterruptsFadeAndAlwaysVisibleIgnoresHudLifetime(){
        UUID id=UUID.randomUUID();CumulativeDamageTracker.record(id,"Player",1,200,1,false);CumulativeDamageTracker.tick();CumulativeDamageTracker.tick();
        assertTrue(CumulativeDamageTracker.opacity()<1);
        CumulativeDamageTracker.record(id,"Player",1,200,1,true);assertEquals(1,CumulativeDamageTracker.opacity());
        for(int i=0;i<50;i++)CumulativeDamageTracker.tick();
        assertEquals("2.0",CumulativeDamageTracker.visible("{damage}"));
    }

    @Test void expiryDeathAndManualResetFadeLastSnapshot(){
        UUID id=UUID.randomUUID(),other=UUID.randomUUID();
        CumulativeDamageTracker.record(id,"Player",5,2,100,true);CumulativeDamageTracker.tick();CumulativeDamageTracker.tick();
        assertEquals("5.0",CumulativeDamageTracker.visible("{damage}"));assertEquals(1,CumulativeDamageTracker.opacity());
        CumulativeDamageTracker.record(id,"Player",5,100,100,true);CumulativeDamageTracker.reset(other);assertEquals(1,CumulativeDamageTracker.opacity());
        CumulativeDamageTracker.reset(id);CumulativeDamageTracker.tick();assertEquals(.95,CumulativeDamageTracker.opacity(),1e-9);
        CumulativeDamageTracker.record(id,"Player",1,100,100,true);CumulativeDamageTracker.resetAll(true);assertEquals("1.0",CumulativeDamageTracker.visible("{damage}"));
    }

    @Test void deathBeforeDelayedKillingHitIncludesFinalDamageAndFades(){
        UUID id=UUID.randomUUID();
        CumulativeDamageTracker.record(id,"Player",5,200,100,true);
        DamageHud.playerDied(id);
        assertTrue(DamageHud.deathMarked(id));assertEquals(5,CumulativeDamageTracker.total(id));
        DamageHud.recordTrackerHit(id,"Player",3,200,100,true,false);
        assertEquals(0,CumulativeDamageTracker.total(id));
        assertEquals("8.0",CumulativeDamageTracker.visible("{damage}"));assertEquals(1,CumulativeDamageTracker.opacity());
        CumulativeDamageTracker.tick();assertEquals(.95,CumulativeDamageTracker.opacity(),1e-9);
    }

    @Test void otherDeathAndRespawnDoNotPoisonActiveTarget(){
        UUID active=UUID.randomUUID(),other=UUID.randomUUID();
        CumulativeDamageTracker.record(active,"Active",4,200,100,true);
        DamageHud.playerDied(other);assertEquals("Active",CumulativeDamageTracker.visible("{player}"));assertEquals(1,CumulativeDamageTracker.opacity());
        DamageHud.playerDied(active);DamageHud.playerRevived(active);
        DamageHud.recordTrackerHit(active,"Active",2,200,100,true,false);
        assertFalse(DamageHud.deathMarked(active));assertEquals(6,CumulativeDamageTracker.total(active));assertEquals(1,CumulativeDamageTracker.opacity());
    }
}
