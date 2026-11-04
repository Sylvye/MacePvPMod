package dev.sylvyespvphud;

public enum OutlineRenderMode {
    WIREFRAME("Wireframe"), TINT("Tint");

    private final String label;
    OutlineRenderMode(String label) { this.label = label; }
    public String label() { return label; }
    public OutlineRenderMode next() { return this == WIREFRAME ? TINT : WIREFRAME; }
}
