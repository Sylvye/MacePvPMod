package dev.macepvpmod;

public record ReachOutlineConfig(int schemaVersion, boolean enabled, int color, double intensity, int thickness,
                                 boolean topFacesOnly, HardToReachMode hardToReachMode, double minimumReachableArea) {
    public static final int MAX_THICKNESS = 10;

    public static ReachOutlineConfig defaults() { return new ReachOutlineConfig(1,true,0x66ccff,.22,5,false,HardToReachMode.FADE_FACES,.5); }
    public ReachOutlineConfig validated() {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported configuration version: " + schemaVersion);
        double alpha = Double.isFinite(intensity) ? Math.clamp(intensity, .02, 1) : .22;
        double area = Double.isFinite(minimumReachableArea)&&minimumReachableArea>0 ? Math.clamp(minimumReachableArea,.01,1) : .5;
        HardToReachMode mode = hardToReachMode==null ? HardToReachMode.FADE_FACES : hardToReachMode;
        return new ReachOutlineConfig(1,enabled,Math.clamp(color,0,0xffffff),alpha,Math.clamp(thickness,1,MAX_THICKNESS),topFacesOnly,mode,area);
    }
    public int argb() { return ((int)Math.round(intensity * 255) << 24) | color; }
}
