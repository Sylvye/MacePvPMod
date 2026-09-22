package dev.sylvyespvphud;

public record TrackerConfig(int schemaVersion, boolean enabled, int radius, double opacity, int iconSize,
                            boolean onlyWhenPlayerListHeld, TrackerDisplayMode displayMode, boolean hideLocatorBar,
                            double distanceScalingStrength, boolean showDistance, boolean hideDistantPlayers,
                            double hideStartDistance, int maxVisiblePlayers) {
    public static TrackerConfig defaults() {
        return new TrackerConfig(3,true,48,.9,12,true,TrackerDisplayMode.HEADS,false,.25,false,false,1000,0);
    }
    public TrackerConfig validated() {
        if(schemaVersion!=3)throw new IllegalArgumentException("Unsupported Player Tracker configuration version: "+schemaVersion);
        return new TrackerConfig(3,enabled,Math.clamp(radius,16,160),HudStyle.limit(opacity,.05,1,.9),
                Math.clamp(iconSize,6,24),onlyWhenPlayerListHeld,displayMode==null?TrackerDisplayMode.HEADS:displayMode,hideLocatorBar,
                HudStyle.limit(distanceScalingStrength,0,1,.25),showDistance,hideDistantPlayers,
                HudStyle.limit(hideStartDistance,50,10000,1000),Math.clamp(maxVisiblePlayers,0,100));
    }
}
