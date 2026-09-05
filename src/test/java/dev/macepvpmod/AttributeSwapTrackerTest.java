package dev.macepvpmod;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttributeSwapTrackerTest {
    @Test void clickThenDifferentSlotTriggersOnlyOnce() {
        var tracker = new AttributeSwapTracker();
        tracker.click(0);
        assertFalse(tracker.select(0));
        assertTrue(tracker.select(1));
        assertFalse(tracker.select(2));
    }
    @Test void nextTickAndSlotBeforeClickDoNotTrigger() {
        var tracker = new AttributeSwapTracker();
        assertFalse(tracker.select(1));
        tracker.click(1);
        assertFalse(tracker.select(1));
        tracker.reset();
        assertFalse(tracker.select(2));
    }
    @Test void anotherClickCanTriggerAnotherSwap() {
        var tracker = new AttributeSwapTracker();
        tracker.click(8);
        assertTrue(tracker.select(0));
        tracker.click(0);
        assertTrue(tracker.select(8));
    }
    @Test void successfulHitCanBeRequired() {
        var tracker = new AttributeSwapTracker();
        tracker.click(0);
        assertFalse(tracker.select(1, true, true));
        tracker.successfulHit();
        assertTrue(tracker.select(1, true, true));
    }
    @Test void destinationCanBeRequiredToBeAWeapon() {
        var tracker = new AttributeSwapTracker();
        tracker.click(0);
        assertFalse(tracker.select(1, false, false));
        assertTrue(tracker.select(1, false, true));
    }
}
