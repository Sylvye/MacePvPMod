package dev.macepvpmod;

public record ReachOutlineConfig(int schemaVersion, boolean enabled, int color, double intensity, int thickness) {
    public static final int MAX_THICKNESS = 10;

    public static ReachOutlineConfig defaults() { return new ReachOutlineConfig(1, true, 0x66ccff, .22, 5); }
    public ReachOutlineConfig validated() {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported configuration version: " + schemaVersion);
        double alpha = Double.isFinite(intensity) ? Math.clamp(intensity, .02, 1) : .22;
        return new ReachOutlineConfig(1, enabled, Math.clamp(color, 0, 0xffffff), alpha, Math.clamp(thickness, 1, MAX_THICKNESS));
    }
    public int argb() { return ((int)Math.round(intensity * 255) << 24) | color; }
}
