package dev.sylvyespvphud;

public record LiquidSourceConfig(boolean enabled, boolean water, boolean lava, int maximumSources,
                                 OutlineRenderMode renderMode, int color, double intensity, int thickness,
                                 boolean topFacesOnly, HardToReachMode hardToReachMode, double minimumReachableArea) {
    public static final int MAX_SOURCES = 64;

    public static LiquidSourceConfig defaults() {
        return new LiquidSourceConfig(true, true, true, 8, OutlineRenderMode.WIREFRAME,
                0x66ccff, .22, 5, false, HardToReachMode.FADE_FACES, .5);
    }

    public LiquidSourceConfig validated() {
        double alpha = Double.isFinite(intensity) ? Math.clamp(intensity, .02, 1) : .22;
        double area = Double.isFinite(minimumReachableArea) && minimumReachableArea > 0
                ? Math.clamp(minimumReachableArea, .01, 1) : .5;
        return new LiquidSourceConfig(enabled, water, lava, Math.clamp(maximumSources, 1, MAX_SOURCES),
                renderMode == null ? OutlineRenderMode.WIREFRAME : renderMode,
                Math.clamp(color, 0, 0xffffff), alpha, Math.clamp(thickness, 1, ReachOutlineConfig.MAX_THICKNESS),
                topFacesOnly, hardToReachMode == null ? HardToReachMode.FADE_FACES : hardToReachMode, area);
    }

    public int argb() { return ((int)Math.round(intensity * 255) << 24) | color; }
}
