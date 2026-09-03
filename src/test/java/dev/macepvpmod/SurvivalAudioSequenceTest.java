package dev.macepvpmod;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurvivalAudioSequenceTest {
    @Test void severityUsesRemainingHpRelativeToConfiguredThreshold() {
        assertEquals(0, SurvivalAudioSequence.severity(10, 20, 50));
        assertEquals(.5, SurvivalAudioSequence.severity(5, 20, 50));
        assertEquals(.9, SurvivalAudioSequence.severity(1, 20, 50));
        assertEquals(.5, SurvivalAudioSequence.severity(10, 40, 50));
        assertEquals(.5, SurvivalAudioSequence.severity(2.5, 20, 25));
        assertEquals(0, SurvivalAudioSequence.severity(15, 20, 50));
        assertEquals(1, SurvivalAudioSequence.severity(0, 20, 50));
        assertEquals(0, SurvivalAudioSequence.severity(0, 20, 0));
    }
    @Test void fixedHpKeepsVolumeAndRateConstantRegardlessOfTime() {
        for (double severity : new double[]{0, .5, 1}) {
            var sequence = new SurvivalAudioSequence();
            int interval = (int)Math.round(15 - 10 * severity), notes = 0;
            for (int tick = 0; tick < 1000; tick++) {
                var cue = sequence.tick(true, .6, .75, .25, severity);
                if (tick % interval != 0) { assertNull(cue); continue; }
                assertNotNull(cue);
                assertEquals(notes++ % 2 == 1, cue.bass());
                assertEquals(.6 * (1.0 / 6 + 5.0 / 6 * severity), cue.volume(), .00001);
            }
        }
    }
    @Test void damageAcceleratesPendingNoteAndHealingSlowsIt() {
        var sequence = new SurvivalAudioSequence();
        assertEquals(.1, sequence.tick(true, .6, .75, .25, 0).volume(), .00001);
        for (int tick = 1; tick < 5; tick++) assertNull(sequence.tick(true, .6, .75, .25, 0));
        var critical = sequence.tick(true, .6, .75, .25, 1);
        assertNotNull(critical); assertTrue(critical.bass()); assertEquals(.6, critical.volume(), .00001);
        for (int tick = 1; tick < 15; tick++) assertNull(sequence.tick(true, .6, .75, .25, 0));
        var recovered = sequence.tick(true, .6, .75, .25, 0);
        assertNotNull(recovered); assertFalse(recovered.bass()); assertEquals(.1, recovered.volume(), .00001);
    }
    @Test void customIntervalsAreRespected() {
        for (double severity : new double[]{0, 1}) {
            var sequence = new SurvivalAudioSequence();
            int interval = severity == 0 ? 30 : 10;
            for (int tick = 0; tick < 240; tick++) {
                var cue = sequence.tick(true, .6, 1.5, .5, severity);
                assertEquals(tick % interval == 0, cue != null);
            }
        }
    }
    @Test void pitchVariationStaysWithinFivePercentOfEachBase() {
        for (double base : new double[]{.5, .75, 1, 1.5, 2}) {
            assertEquals(base * .95, SurvivalAudioSequence.variedPitch(base, 0), .00001);
            assertEquals(base, SurvivalAudioSequence.variedPitch(base, .5), .00001);
            assertEquals(base * 1.05, SurvivalAudioSequence.variedPitch(base, 1), .00001);
        }
    }
    @Test void recoveryAndMuteResetAlternationWithoutDelayingCriticalAudio() {
        var sequence = new SurvivalAudioSequence();
        sequence.tick(true, .6, .75, .25, 0);
        assertNull(sequence.tick(false, .6, .75, .25, 0));
        var restarted = sequence.tick(true, .6, .75, .25, 1);
        assertFalse(restarted.bass()); assertEquals(.6, restarted.volume(), .00001);
        assertNull(sequence.tick(true, 0, .75, .25, 1));
        restarted = sequence.tick(true, .3, .75, .25, .5);
        assertFalse(restarted.bass()); assertEquals(.175, restarted.volume(), .00001);
    }
}
