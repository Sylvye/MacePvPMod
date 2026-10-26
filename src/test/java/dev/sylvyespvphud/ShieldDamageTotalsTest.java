package dev.sylvyespvphud;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShieldDamageTotalsTest {
    private final UUID first=UUID.randomUUID(),second=UUID.randomUUID();
    @Test void measuredCorrectionDoesNotRefreshEitherTimer() {
        var totals=new ShieldDamageTotals(0);
        Object receipt=totals.record(first,"First",12,3,3,false);
        totals.tick();totals.tick();totals.adjust(first,receipt,-9);
        assertEquals(3,totals.total(first));assertEquals(3,totals.amount());
        totals.tick();assertEquals(0,totals.total(first));assertEquals(0,totals.opacity());
    }
    @Test void correctionDoesNotReviveAnExpiredOrResetStreak() {
        var totals=new ShieldDamageTotals(0);
        Object old=totals.record(first,"First",12,1,1,false);totals.tick();
        totals.adjust(first,old,-9);assertTrue(Double.isNaN(totals.amount()));
        totals.record(first,"First",7,10,10,false);totals.adjust(first,old,-9);
        assertEquals(7,totals.total(first));assertEquals(7,totals.amount());
        totals.reset(first);totals.adjust(first,old,20);assertEquals(0,totals.total(first));
    }
    @Test void lateCorrectionKeepsOtherTargetSelectedAndPreservesBrokenFinalDisplay() {
        var totals=new ShieldDamageTotals(0);
        Object receipt=totals.record(first,"First",12,10,10,false);
        totals.record(second,"Second",8,10,10,false);totals.adjust(first,receipt,-9);
        assertEquals(second,totals.active());assertEquals(8,totals.amount());assertEquals(3,totals.total(first));
        Object finalReceipt=totals.record(first,"First",4,10,10,false);totals.broken(first);
        totals.adjust(first,finalReceipt,-2);assertEquals(5,totals.amount());assertEquals(0,totals.total(first));
    }
    @Test void spamRefreshesTheSumAndBothTimers() {
        var totals=new ShieldDamageTotals(0);
        totals.record(first,"First",4,3,3,false);
        totals.tick();totals.tick();
        totals.record(first,"First",5,3,3,false);
        totals.tick();totals.tick();
        assertEquals(9,totals.total(first));
        assertEquals("First: 9.0",totals.visible("{player}: {damage}"));
        totals.tick();assertEquals(0,totals.total(first));assertEquals("",totals.visible("{damage}"));
        totals.record(first,"First",2,3,3,false);assertEquals(2,totals.total(first));
    }
    @Test void targetsHaveIndependentSumsAndHealthHitResetsOnlyOne() {
        var totals=new ShieldDamageTotals(0);
        totals.record(first,"First",4,10,10,false);
        totals.record(second,"Second",8,10,10,false);
        totals.reset(first);totals.dismiss();
        assertEquals(0,totals.total(first));assertEquals(8,totals.total(second));
        totals.record(second,"Second",3,10,10,false);
        assertEquals(11,totals.amount());assertEquals(second,totals.active());
    }
    @Test void trackerStorageOutlivesHudAndHitStateIsIndependent() {
        var tracker=new ShieldDamageTotals(2);var hits=new ShieldDamageTotals(0);
        tracker.record(first,"First",4,10,2,false);hits.record(first,"First",4,2,2,false);
        for(int i=0;i<4;i++){tracker.tick();hits.tick();}
        assertEquals(4,tracker.total(first));assertEquals(0,hits.total(first));assertEquals(0,tracker.opacity());
        tracker.record(first,"First",2,10,2,false);assertEquals(6,tracker.amount());assertEquals(1,tracker.opacity());
    }
    @Test void breakRetainsFinalNumberButNextHitStartsFresh() {
        var totals=new ShieldDamageTotals(0);
        totals.record(first,"First",9,10,3,false);totals.broken(first);
        assertEquals(0,totals.total(first));assertEquals(9,totals.amount());
        totals.tick();totals.tick();assertEquals(9,totals.amount());
        totals.tick();assertTrue(Double.isNaN(totals.amount()));
        totals.record(first,"First",2,10,3,false);assertEquals(2,totals.amount());
    }
    @Test void brokenAlwaysVisibleResultPersistsUntilResetOrReplacement() {
        var totals=new ShieldDamageTotals(2);
        totals.record(first,"First",9,2,2,true);totals.broken(first);
        for(int i=0;i<20;i++)totals.tick();assertEquals(9,totals.amount());
        totals.reset(first);totals.tick();totals.tick();assertEquals(0,totals.opacity());
    }
    @Test void storageExpiryOverridesAlwaysVisibleAndInvalidDamageDoesNotRefresh() {
        var totals=new ShieldDamageTotals(0);
        totals.record(first,"First",3,2,8,true);totals.tick();
        totals.record(first,"First",Double.NaN,20,20,true);totals.record(first,"First",0,20,20,true);
        totals.tick();assertEquals(0,totals.total(first));assertEquals(0,totals.opacity());
    }
}
