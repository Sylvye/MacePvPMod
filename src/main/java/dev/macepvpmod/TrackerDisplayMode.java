package dev.macepvpmod;

public enum TrackerDisplayMode {
    HEADS("Heads"), COLORS("Colors");
    private final String label;
    TrackerDisplayMode(String label) { this.label=label; }
    public String label() { return label; }
    public TrackerDisplayMode next() { return values()[(ordinal()+1)%values().length]; }
}
