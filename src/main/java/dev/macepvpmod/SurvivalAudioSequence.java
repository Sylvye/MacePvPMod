package dev.macepvpmod;

/** Remaining health controls volume and cadence; elapsed time only schedules notes. */
final class SurvivalAudioSequence {
    private int ticksSinceNote;
    private boolean started;
    private boolean bass;
    record Cue(boolean bass, float volume) {}

    Cue tick(boolean active, double volume, double startInterval, double endInterval, double severity) {
        if (!active || volume <= 0) { reset(); return null; }
        double progress = Math.max(0, Math.min(1, severity));
        // Recalculate every tick so damage and healing also change the pending interval.
        int interval = Math.max(1, (int)Math.round(20 * (startInterval + (endInterval - startInterval) * progress)));
        if (started && ++ticksSinceNote < interval) return null;
        started = true;
        ticksSinceNote = 0;
        float gain = (float)(volume * (1.0 / 6 + 5.0 / 6 * progress));
        var cue = new Cue(bass, gain);
        bass = !bass;
        return cue;
    }
    static double severity(double health, double maxHealth, double thresholdPercent) {
        double threshold = maxHealth * thresholdPercent / 100;
        if (!Double.isFinite(threshold) || threshold <= 0 || !Double.isFinite(health)) return 0;
        return Math.max(0, Math.min(1, (threshold - health) / threshold));
    }
    static float variedPitch(double basePitch, double randomUnit) {
        return (float)(basePitch * (.95 + .1 * randomUnit));
    }
    void reset() { ticksSinceNote = 0; started = false; bass = false; }
}
