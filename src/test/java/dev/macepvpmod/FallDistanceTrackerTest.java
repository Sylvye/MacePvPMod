package dev.macepvpmod;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FallDistanceTrackerTest {
    @Test void shallowGlideCountsFromFirstMovement() {
        var tracker = new FallDistanceTracker();
        for (int i = 0; i < 8; i++) tracker.move(-.2, false);
        assertEquals(1.6, tracker.distance(), 1e-9);
        for (int i = 0; i < 92; i++) tracker.move(-.2, false);
        assertEquals(20, tracker.distance(), 1e-9);
    }
    @Test void changingDescentSpeedDoesNotDiscardHeight() {
        var tracker = new FallDistanceTracker();
        tracker.move(-10, false); tracker.move(-.1, false); tracker.move(0, false); tracker.move(-2, false);
        assertEquals(12.1, tracker.distance(), 1e-9);
    }
    @Test void ascentAndContactStartNewFalls() {
        var tracker = new FallDistanceTracker();
        tracker.move(-10, false); tracker.move(.1, false);
        assertEquals(0, tracker.distance());
        tracker.move(-2, false); assertEquals(2, tracker.distance());
        tracker.move(-1, true); assertEquals(0, tracker.distance());
        tracker.move(-3, false); tracker.reset(); assertEquals(0, tracker.distance());
    }
}
