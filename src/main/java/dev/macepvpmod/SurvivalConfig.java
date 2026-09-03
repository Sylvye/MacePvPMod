package dev.macepvpmod;

public record SurvivalConfig(int schemaVersion, boolean retotemEnabled, boolean healingEnabled,
        String retotemText, int retotemColor, double retotemSize, double retotemX, double retotemY,
        String healthText, int healthColor, String saturationText, int saturationColor,
        String combinedText, int combinedColor, double healingSize, double healingX, double healingY,
        double healthPercent, double saturationThreshold, double harpVolume, double bassVolume, double harpPitch, double bassPitch, double audioStartInterval, double audioEndInterval) {
    public static SurvivalConfig defaults() {
        return new SurvivalConfig(1, true, true, "EQUIP TOTEM!", 0xffff55, 1.5, 50, 18,
                "LOW HEALTH !", 0xff3333, "Low saturation!", 0xff9900,
                "LOW HEALTH + SAT!", 0xaa0000, 1.5, 50, 10, 50, 10, .5, .5, 1, 1, .75, .25);
    }
    public SurvivalConfig validated() {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported configuration version");
        return new SurvivalConfig(1, retotemEnabled, healingEnabled,
                text(retotemText, "EQUIP TOTEM!"), retotemColor & 0xffffff,
                clamp(retotemSize, .5, 4, 1.5), clamp(retotemX, 0, 100, 50), clamp(retotemY, 0, 100, 18),
                text(healthText, "LOW HEALTH !"), healthColor & 0xffffff,
                text(saturationText, "Low saturation!"), saturationColor & 0xffffff,
                text(combinedText, "LOW HEALTH + SAT!"), combinedColor & 0xffffff,
                clamp(healingSize, .5, 4, 1.5), clamp(healingX, 0, 100, 50), clamp(healingY, 0, 100, 10),
                clamp(healthPercent, 0, 100, 50), clamp(saturationThreshold, 0, 20, 10), clamp(harpVolume, 0, 1, .5), clamp(bassVolume, 0, 1, .5),
                clamp(harpPitch, .5, 2, 1), clamp(bassPitch, .5, 2, 1),
                clamp(audioStartInterval, .05, 3, .75),
                Math.min(clamp(audioStartInterval, .05, 3, .75), clamp(audioEndInterval, .05, 3, .25)));
    }
    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.substring(0, Math.min(80, value.length()));
    }
    private static double clamp(double n, double min, double max, double fallback) {
        return Double.isFinite(n) ? Math.max(min, Math.min(max, n)) : fallback;
    }
}
