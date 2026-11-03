package dev.sylvyespvphud;

import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttackHistoryTest {
    private final UUID first=UUID.randomUUID(),second=UUID.randomUUID();
    private final AttackHistory<Double> history=new AttackHistory<>();
    private AttackHistory.Entry<Double> add(UUID id,double amount,long now){return history.add(id,amount,now,2000,200);}
    private AttackHistory.Evidence<Double> confirm(UUID id,AttackHistory.Kind kind,long now){return history.confirm(id,kind,a->true,now);}
    private void health(UUID id,long now){confirm(id,AttackHistory.Kind.HEALTH,now);}

    @Test void weakFollowupCannotReplaceAnUnconfirmedSmash() {
        var smash=add(first,40,0);var weak=add(first,.7,100);health(first,300);
        var results=history.resolve(500);
        assertEquals(1,results.size());assertSame(smash,results.getFirst().entry());
        assertEquals(java.util.List.of(weak),history.entries());
    }
    @Test void differingShieldEstimatesDoNotSuppressConfirmedBlocks() {
        var smash=add(first,40,0);add(first,.7,100);confirm(first,AttackHistory.Kind.BLOCK,300);
        var results=history.resolve(500);assertEquals(1,results.size());assertSame(smash,results.getFirst().entry());
        assertEquals(AttackHistory.Kind.BLOCK,results.getFirst().evidence().kind);
    }
    @Test void subsequentEvidenceResolvesSavedSnapshotsInOrder() {
        var smash=add(first,40,0);var weak=add(first,.7,100);health(first,300);
        assertSame(smash,history.resolve(500).getFirst().entry());health(first,550);
        assertSame(weak,history.resolve(750).getFirst().entry());assertTrue(history.resolve(1000).isEmpty());
    }
    @Test void laterSwingsDoNotContaminateAnEarlierConfirmation() {
        var smash=add(first,40,0);health(first,100);add(first,.7,150);
        var result=history.resolve(300);assertEquals(1,result.size());assertSame(smash,result.getFirst().entry());
        assertEquals(.7,history.entries().getFirst().value);
    }
    @Test void confirmationCannotConsumeASwingCreatedAfterItArrived() {
        add(first,40,0);health(first,100);var newer=add(first,.7,150);
        health(first,200);var results=history.resolve(300);assertEquals(1,results.size());
        assertSame(newer,history.resolve(400).getFirst().entry());
    }
    @Test void eachTargetResolvesIndependently() {
        add(first,40,0);add(first,.7,50);add(second,8,0);health(first,100);health(second,100);
        var result=history.resolve(300);assertEquals(2,result.size());
        assertEquals(40,result.getFirst().entry().value);assertEquals(second,result.get(1).entry().target);
    }
    @Test void newConfirmationsCannotPostponeAnOlderResult() {
        var oldest=add(first,10,0);health(first,100);add(first,20,150);health(first,200);
        var result=history.resolve(300);assertEquals(1,result.size());assertSame(oldest,result.getFirst().entry());
        assertEquals(20,history.resolve(400).getFirst().entry().value);
    }
    @Test void continuousFightsReportDuringCombatForBothKindsAndVariableDamage() {
        for(var kind:AttackHistory.Kind.values()) {
            history.clear();var results=new ArrayList<AttackHistory.Resolution<Double>>();
            for(int i=0;i<60;i++) {
                long now=i*100;
                add(first,5+i%3,now);confirm(first,kind,now+25);
                var due=history.resolve(now+50);results.addAll(due);
                for(var result:due)assertTrue(now+50-result.evidence().received<=250,"Result missed its own deadline");
                if(i>=3)assertFalse(due.isEmpty(),"Continuous confirmations postponed output during combat");
            }
            assertEquals(58,results.size());
            assertEquals(2,history.resolve(6500).size());assertTrue(history.resolve(7000).isEmpty());
        }
    }
    @Test void oldUnmatchedSwingCannotPoisonNewConfirmationsAtExpiry() {
        add(first,40,0);var fresh=add(first,8,1600);health(first,1700);
        var result=history.resolve(2100);assertEquals(1,result.size());assertSame(fresh,result.getFirst().entry());
        assertFalse(result.getFirst().evidence().cancelled);
    }
    @Test void oneExpiredCandidateDoesNotCancelTheRemainingCandidate() {
        add(first,40,0);var newer=add(first,.7,500);health(first,1000);
        assertSame(newer,history.resolve(2001).getFirst().entry());
    }
    @Test void conflictingHealthAndBlockEvidenceDoesNotCountOneAttackTwice() {
        add(first,20,0);health(first,100);confirm(first,AttackHistory.Kind.BLOCK,120);
        assertTrue(history.resolve(300).isEmpty());assertTrue(history.entries().isEmpty());
    }
    @Test void aConflictDoesNotDiscardAnotherTargetsConfirmation() {
        add(first,20,0);health(first,100);confirm(first,AttackHistory.Kind.BLOCK,120);
        add(second,30,0);health(second,100);
        assertEquals(second,history.resolve(300).getFirst().entry().target);
    }
    @Test void aConflictDoesNotDiscardANewerAttackOnTheSameTarget() {
        add(first,20,0);health(first,100);confirm(first,AttackHistory.Kind.BLOCK,120);
        var newer=add(first,30,200);health(first,250);
        assertTrue(history.resolve(300).isEmpty());
        assertSame(newer,history.resolve(450).getFirst().entry());
    }
    @Test void contradictoryLaterEvidenceCannotEraseAnEarlierIndependentBlock() {
        add(first,10,0);add(first,20,50);
        history.confirm(first,AttackHistory.Kind.BLOCK,a->a==10,100);
        history.confirm(first,AttackHistory.Kind.BLOCK,a->a==20,110);health(first,120);
        var result=history.resolve(400);assertEquals(1,result.size());assertEquals(10,result.getFirst().entry().value);
        assertEquals(AttackHistory.Kind.BLOCK,result.getFirst().evidence().kind);
        assertTrue(history.resolve(500).isEmpty());
    }
    @Test void duplicateSameKindEvidenceCannotConsumeOneAttackTwice() {
        add(first,10,0);confirm(first,AttackHistory.Kind.BLOCK,100);confirm(first,AttackHistory.Kind.BLOCK,110);
        assertEquals(1,history.resolve(400).size());assertTrue(history.resolve(500).isEmpty());
    }
    @Test void cancellingOneObservationDoesNotCancelEveryCandidate() {
        var firstAttack=add(first,10,0);add(first,20,50);
        var stale=confirm(first,AttackHistory.Kind.BLOCK,100);stale.cancelled=true;
        health(first,120);assertSame(firstAttack,history.resolve(400).getFirst().entry());
    }
    @Test void lateConfirmationCannotReviveAnExpiredAttack() {
        add(first,20,0);assertNull(confirm(first,AttackHistory.Kind.HEALTH,2000));
        assertTrue(history.resolve(2200).isEmpty());
    }
    @Test void discardAndClearCancelOutstandingEvidence() {
        add(first,20,0);add(second,30,0);health(first,100);health(second,100);
        history.discard(first);assertEquals(second,history.resolve(300).getFirst().entry().target);
        add(second,30,400);health(second,500);history.clear();assertTrue(history.resolve(1000).isEmpty());
    }
    @Test void capacityRemainsBoundedWithoutQuarantiningAnActiveTarget() {
        for(int i=0;i<AttackHistory.PER_TARGET_LIMIT+10;i++)assertNotNull(add(first,i,0));
        assertEquals(AttackHistory.PER_TARGET_LIMIT,history.entries().size());
        health(first,100);assertEquals(10,history.resolve(300).getFirst().entry().value);
        assertNotNull(add(first,99,350));
    }
    @Test void capacityPressurePreservesAnAlreadyConfirmedSmashUntilItSettles() {
        var smash=add(first,40,0);
        for(int i=1;i<AttackHistory.PER_TARGET_LIMIT;i++)add(first,.7,50);
        health(first,100);
        for(int i=0;i<40;i++)assertNotNull(add(first,1.2,150));
        assertSame(smash,history.resolve(300).getFirst().entry());
    }
    @Test void manySwingsWithSparseServerConfirmationsKeepReportingDuringCombat() {
        int reports=0;
        for(int i=0;i<150;i++) {
            long now=i*50;add(first,5+i%3,now);
            if(i%10==0)confirm(first,AttackHistory.Kind.BLOCK,now+25);
            var results=history.resolve(now+50);reports+=results.size();
            for(var result:results)assertTrue(now+50-result.evidence().received<=250);
            assertTrue(history.entries().size()<=AttackHistory.PER_TARGET_LIMIT);
        }
        assertEquals(15,reports,"Unconfirmed swings must not cause periodic target-wide blackouts");
    }
    @Test void settlesAfterRealElapsedTimeForTypicalNetworkDelays() {
        for(int delay:new int[]{100,300,800}) {
            history.clear();add(first,40,0);health(first,delay);
            assertTrue(history.resolve(delay+199).isEmpty());assertEquals(40,history.resolve(delay+200).getFirst().entry().value);
        }
    }
    @Test void responseAtEndOfRetentionStillHasTimeToSettle() {
        add(first,40,0);health(first,1999);
        assertTrue(history.resolve(2100).isEmpty());assertEquals(40,history.resolve(2199).getFirst().entry().value);
    }
    @Test void retentionAndSettlingAdaptToPingWithSafeBounds() {
        assertEquals(2000,CombatTiming.retention(-1));assertEquals(200,CombatTiming.settling(-1));
        assertEquals(2000,CombatTiming.retention(100));assertEquals(150,CombatTiming.settling(100));
        assertEquals(3000,CombatTiming.retention(1250));assertEquals(500,CombatTiming.settling(1250));
        assertEquals(6000,CombatTiming.retention(Integer.MAX_VALUE));
    }
}
