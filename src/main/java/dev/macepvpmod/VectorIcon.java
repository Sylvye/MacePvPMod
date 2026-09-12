package dev.macepvpmod;

public enum VectorIcon {
    CIRCLE("Circle"), CROSSHAIR("Crosshair"), STAR("Star");
    private final String label;
    VectorIcon(String label) { this.label = label; }
    public String label() { return label; }
    public VectorIcon next() { return values()[(ordinal() + 1) % values().length]; }
}
