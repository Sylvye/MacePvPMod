package dev.macepvpmod;

public record DamageConfig(int schemaVersion, boolean fallEnabled, int fallColor, double fallSize,
        int fallX, int fallY, boolean hitEnabled, int hitColor, double hitSize, int hitX, int hitY, int hitSeconds, boolean calculatedDamage, String fallTemplate, String hitTemplate) {
    public DamageConfig(int schemaVersion, boolean fallEnabled, int fallColor, double fallSize,
            int fallX, int fallY, boolean hitEnabled, int hitColor, double hitSize, int hitX, int hitY, int hitSeconds, boolean calculatedDamage) {
        this(schemaVersion, fallEnabled, fallColor, fallSize, fallX, fallY, hitEnabled, hitColor, hitSize, hitX, hitY, hitSeconds, calculatedDamage, "{blocks} blocks", "{damage} damage");
    }
    public DamageConfig(int schemaVersion, boolean fallEnabled, int fallColor, double fallSize,
            int fallX, int fallY, boolean hitEnabled, int hitColor, double hitSize, int hitX, int hitY, int hitSeconds) {
        this(schemaVersion, fallEnabled, fallColor, fallSize, fallX, fallY, hitEnabled, hitColor, hitSize, hitX, hitY, hitSeconds, false);
    }
    public static DamageConfig defaults() { return new DamageConfig(1, true, 0xffffff, 1, 0, 14, true, 0xff6666, 1, 0, 28, 3); }
    public DamageConfig validated() {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported configuration version");
        return new DamageConfig(1, fallEnabled, clamp(fallColor, 0, 0xffffff), size(fallSize),
                clamp(fallX, -2000, 2000), clamp(fallY, -2000, 2000), hitEnabled,
                clamp(hitColor, 0, 0xffffff), size(hitSize), clamp(hitX, -2000, 2000), clamp(hitY, -2000, 2000), clamp(hitSeconds, 1, 10), calculatedDamage, template(fallTemplate, false), template(hitTemplate, true));
    }
    private static String template(String s, boolean hit) { return DamageText.error(s, hit).isEmpty() && s.length() <= 160 ? s : hit ? "{damage} damage" : "{blocks} blocks"; }
    private static int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }
    private static double size(double n) { return Double.isFinite(n) ? Math.max(.5, Math.min(4, n)) : 1; }
}
