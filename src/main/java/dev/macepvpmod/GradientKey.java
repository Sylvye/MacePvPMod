package dev.macepvpmod;

public record GradientKey(double position, int color) {
    GradientKey validated() {
        if (!Double.isFinite(position) || position < 0 || position > 1 || color < 0 || color > 0xffffff)
            throw new IllegalArgumentException("Invalid gradient key");
        return this;
    }
}
