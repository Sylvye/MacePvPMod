package dev.sylvyespvphud;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttackHistoryTest {
    private final UUID first=UUID.randomUUID(),second=UUID.randomUUID();
    private final AttackHistory<Double> history=new AttackHistory<>();
    private AttackHistory.Entry<Double> add(UUID id,double amount,long now){return history.add(id,amount,now,2000,200);}
    private void health(UUID id,long now){history.confirm(id,AttackHistory.Kind.HEALTH,a->true,now);}
    private java.util.List<AttackHistory.Resolution<Double>> resolve(long now){return history.resolve(now,a->a,a->a);}

    @Test void weakFollowupCannotReplaceAnUnconfirmedSmash() {
        add(first,40,0);add(first,.7,100);health(first,300);
        assertTrue(resolve(500).isEmpty());assertEquals(2,history.entries().size());
        assertTrue(resolve(2000).isEmpty());assertTrue(history.entries().isEmpty());
    }
    @Test void subsequentEvidenceCanResolveDifferentSnapshotsInOrder() {
        var smash=add(first,40,0);var weak=add(first,.7,100);health(first,300);
        assertTrue(resolve(500).isEmpty());health(first,550);
        var results=resolve(750);assertEquals(2,results.size());
        assertSame(smash,results.get(0).entry());assertSame(weak,results.get(1).entry());
        assertTrue(resolve(1000).isEmpty());
    }
    @Test void laterSwingsDoNotContaminateAnEarlierConfirmation() {
        var smash=add(first,40,0);health(first,100);add(first,.7,150);
        var result=resolve(300);assertEquals(1,result.size());assertSame(smash,result.getFirst().entry());
        assertEquals(.7,history.entries().getFirst().value);
    }
    @Test void equivalentCandidatesCanResolveWithoutGuessingAnAmount() {
        var oldest=add(first,20,0);add(first,20,50);health(first,100);
        assertSame(oldest,resolve(300).getFirst().entry());
    }
    @Test void eachTargetResolvesIndependently() {
        add(first,40,0);add(first,.7,50);add(second,8,0);health(first,100);health(second,100);
        var result=resolve(300);assertEquals(1,result.size());assertEquals(second,result.getFirst().entry().target);
    }
    @Test void conflictingHealthAndBlockEvidenceDoesNotCountOneAttackTwice() {
        add(first,20,0);health(first,100);history.confirm(first,AttackHistory.Kind.BLOCK,a->true,120);
        assertTrue(resolve(400).isEmpty());assertTrue(history.entries().isEmpty());
    }
    @Test void laterEvidenceConnectingDisjointSubsetsCannotConsumeAnAttackTwice() {
        add(first,10,0);add(first,20,50);
        history.confirm(first,AttackHistory.Kind.BLOCK,a->a==10,100);
        history.confirm(first,AttackHistory.Kind.BLOCK,a->a==20,110);
        health(first,120);
        assertTrue(resolve(400).isEmpty());assertTrue(history.entries().isEmpty());
    }
    @Test void lateConfirmationCannotReviveAnExpiredAttack() {
        add(first,20,0);assertNull(history.confirm(first,AttackHistory.Kind.HEALTH,a->true,2000));
        assertTrue(resolve(2200).isEmpty());
    }
    @Test void expiringOneAmbiguousCandidateDoesNotMakeTheOtherCertain() {
        add(first,40,0);add(first,.7,500);health(first,1000);
        assertTrue(resolve(2001).isEmpty());assertTrue(history.entries().isEmpty());
    }
    @Test void discardAndClearCancelOutstandingEvidence() {
        add(first,20,0);add(second,30,0);health(first,100);health(second,100);
        history.discard(first);assertEquals(second,resolve(300).getFirst().entry().target);
        add(second,30,400);health(second,500);history.clear();assertTrue(resolve(1000).isEmpty());
    }
    @Test void capacityOverflowCannotTurnMissingHistoryIntoAConfidentHit() {
        for(int i=0;i<AttackHistory.PER_TARGET_LIMIT;i++)assertNotNull(add(first,i,0));
        health(first,100);assertNull(add(first,99,150));assertTrue(resolve(400).isEmpty());
        assertNull(add(first,99,500));assertNotNull(add(first,99,2200));
    }
    @Test void settlesAfterRealElapsedTimeForTypicalNetworkDelays() {
        for(int delay:new int[]{100,300,800}) {
            history.clear();add(first,40,0);health(first,delay);
            assertTrue(resolve(delay+199).isEmpty());assertEquals(40,resolve(delay+200).getFirst().entry().value);
        }
    }
    @Test void responseAtEndOfRetentionStillHasTimeToSettle() {
        add(first,40,0);health(first,1999);
        assertTrue(resolve(2100).isEmpty());assertEquals(40,resolve(2199).getFirst().entry().value);
    }
    @Test void retentionAndSettlingAdaptToPingWithSafeBounds() {
        assertEquals(2000,CombatTiming.retention(-1));assertEquals(200,CombatTiming.settling(-1));
        assertEquals(2000,CombatTiming.retention(100));assertEquals(150,CombatTiming.settling(100));
        assertEquals(3000,CombatTiming.retention(1250));assertEquals(500,CombatTiming.settling(1250));
        assertEquals(6000,CombatTiming.retention(Integer.MAX_VALUE));
    }
}
