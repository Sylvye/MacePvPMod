package dev.sylvyespvphud;

public record ReachOutlineConfig(int schemaVersion, boolean enabled, int color, double intensity, int thickness,
                                 boolean topFacesOnly, HardToReachMode hardToReachMode, double minimumReachableArea,
                                 OutlineRenderMode renderMode, LiquidSourceConfig liquidSources) {
    public static final int MAX_THICKNESS = 10;

    public ReachOutlineConfig(int schemaVersion, boolean enabled, int color, double intensity, int thickness,
                              boolean topFacesOnly, HardToReachMode hardToReachMode, double minimumReachableArea) {
        this(schemaVersion, enabled, color, intensity, thickness, topFacesOnly, hardToReachMode,
                minimumReachableArea, OutlineRenderMode.WIREFRAME, LiquidSourceConfig.defaults());
    }
    public static ReachOutlineConfig defaults() { return new ReachOutlineConfig(1,true,0x66ccff,.22,5,false,HardToReachMode.FADE_FACES,.5); }
    public ReachOutlineConfig validated() {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported configuration version: " + schemaVersion);
        double alpha = Double.isFinite(intensity) ? Math.clamp(intensity, .02, 1) : .22;
        double area = Double.isFinite(minimumReachableArea)&&minimumReachableArea>0 ? Math.clamp(minimumReachableArea,.01,1) : .5;
        HardToReachMode mode = hardToReachMode==null ? HardToReachMode.FADE_FACES : hardToReachMode;
        return new ReachOutlineConfig(1,enabled,Math.clamp(color,0,0xffffff),alpha,Math.clamp(thickness,1,MAX_THICKNESS),topFacesOnly,mode,area,
                renderMode == null ? OutlineRenderMode.WIREFRAME : renderMode,
                liquidSources == null ? LiquidSourceConfig.defaults() : liquidSources.validated());
    }
    public ReachOutlineConfig withEnabled(boolean value) {
        return new ReachOutlineConfig(schemaVersion,value,color,intensity,thickness,topFacesOnly,hardToReachMode,minimumReachableArea,renderMode,liquidSources);
    }
    public int argb() { return ((int)Math.round(intensity * 255) << 24) | color; }
}
