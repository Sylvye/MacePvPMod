package dev.macepvpmod;

public record PitchConfig(int schemaVersion, boolean enabled, int width, int thickness,
        int color, double opacity, double targetPitch, double sensitivity,
        int maxDisplacement, boolean thirdPerson) {
    public static PitchConfig defaults() {
        return new PitchConfig(1, true, 100, 1, 0x999999, .4, 40, 2, 60, false);
    }
    public PitchConfig validated() {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported configuration version: " + schemaVersion);
        return new PitchConfig(1, enabled, clamp(width, 10, 400), clamp(thickness, 1, 8),
                clamp(color, 0, 0xffffff), clamp(opacity, .05, 1, .4),
                clamp(targetPitch, -90, 90, 40), clamp(sensitivity, .1, 10, 2),
                clamp(maxDisplacement, 0, 200), thirdPerson);
    }
    private static int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }
    private static double clamp(double n, double min, double max, double fallback) {
        return Double.isFinite(n) ? Math.max(min, Math.min(max, n)) : fallback;
    }
    public int argb() { return ((int) Math.round(opacity * 255) << 24) | color; }
}
