package dev.sylvyespvphud;

public enum TrackerPlacement {
    RING("Ring"), HORIZON("Horizon");

    private final String label;
    TrackerPlacement(String label) { this.label = label; }
    public String label() { return label; }
    public TrackerPlacement next() { return values()[(ordinal() + 1) % values().length]; }
}
