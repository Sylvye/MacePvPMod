package dev.macepvpmod;

public record VectorsConfig(int schemaVersion, boolean enabled, boolean reticleEnabled,
                            boolean velocityEnabled, boolean elytraOnly, boolean spearOnly,
                            VectorIcon icon, int size, int color, double opacity, double stationaryThreshold,
                            double velocityThreshold, String velocityTemplate, ColorScale velocityColors) {
    public static VectorsConfig defaults() {
        return new VectorsConfig(2, false, true, true, true, true, VectorIcon.CIRCLE, 7,
                0xffffff, .9, .05, 0, "{magnitude} blocks/s",
                new ColorScale(ColorMode.GRADIENT, 0xffffff, 0, 40,
                        java.util.List.of(new GradientKey(0, 0x55ff88),
                                new GradientKey(.5, 0xffff55), new GradientKey(1, 0xff5555))));
    }
    public VectorsConfig validated() {
        if (schemaVersion != 2) throw new IllegalArgumentException("Unsupported Vectors configuration version: " + schemaVersion);
        if (VectorsText.error(velocityTemplate).length() > 0) throw new IllegalArgumentException(VectorsText.error(velocityTemplate));
        return new VectorsConfig(2, enabled, reticleEnabled, velocityEnabled, elytraOnly, spearOnly,
                icon == null ? VectorIcon.CIRCLE : icon, Math.clamp(size, 3, 31), color & 0xffffff,
                HudStyle.limit(opacity, .05, 1, .9), HudStyle.limit(stationaryThreshold, 0, 20, .05),
                HudStyle.limit(velocityThreshold, 0, 20, 0), velocityTemplate,
                velocityColors == null ? defaults().velocityColors : velocityColors.validated());
    }
}
