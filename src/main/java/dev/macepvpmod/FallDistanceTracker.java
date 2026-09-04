package dev.macepvpmod;

/** Physical vertical descent since the last ascent or fall-ending contact. */
public final class FallDistanceTracker {
    private double distance;
    public void move(double verticalDisplacement, boolean reset) {
        if (reset || !Double.isFinite(verticalDisplacement) || verticalDisplacement > 0) distance = 0;
        else distance -= verticalDisplacement;
    }
    public double distance() { return distance; }
    public void reset() { distance = 0; }
}
