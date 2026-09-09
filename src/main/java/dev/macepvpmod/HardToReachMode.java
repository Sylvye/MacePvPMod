package dev.macepvpmod;

public enum HardToReachMode {
    RENDER_FULL("Render full"),
    FADE_FACES("Fade faces"),
    DO_NOT_RENDER("Do not render");

    private final String label;

    HardToReachMode(String label) { this.label=label; }
    public String label() { return label; }
    public HardToReachMode next() { return values()[(ordinal()+1)%values().length]; }
}
