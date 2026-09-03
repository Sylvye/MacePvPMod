package dev.macepvpmod;

public final class PitchMath {
    private PitchMath() {}
    public static double offset(double pitch, PitchConfig config) {
        if (!Double.isFinite(pitch)) return 0;
        return Math.max(-config.maxDisplacement(), Math.min(config.maxDisplacement(),
                (config.targetPitch() - pitch) * config.sensitivity()));
    }
}
