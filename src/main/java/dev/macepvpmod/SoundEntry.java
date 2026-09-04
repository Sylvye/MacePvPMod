package dev.macepvpmod;
public record SoundEntry(String sound, double volume, double pitch) {
    public SoundEntry validated() {
        return new SoundEntry(sound == null ? "" : sound, finite(volume, 0, 1, .5), finite(pitch, .5, 2, 1));
    }
    private static double finite(double v, double min, double max, double fallback) { return Double.isFinite(v) ? Math.clamp(v, min, max) : fallback; }
    static java.util.List<SoundEntry> legacy(double hv, double bv, double hp, double bp) {
        return java.util.List.of(new SoundEntry("minecraft:block.note_block.harp", hv, hp), new SoundEntry("minecraft:block.note_block.bass", bv, bp));
    }
}
