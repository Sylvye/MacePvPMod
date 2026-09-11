package dev.macepvpmod;

public record DamageConfig(int schemaVersion, boolean fallEnabled, int fallColor, double fallSize,
        int fallX, int fallY, boolean hitEnabled, int hitColor, double hitSize, int hitX, int hitY, int hitSeconds, boolean calculatedDamage, String fallTemplate, String hitTemplate, double fallThreshold,
        ColorScale fallColors, ColorScale hitColors, boolean maceEnabled, boolean spearEnabled,
        boolean swordAxeEnabled, boolean useEnemyGear, boolean boldCriticalDamage, boolean enabled) {
    public ColorScale effectiveHitColors() {
        return useEnemyGear ? hitColors.withDomain(0,20) : hitColors;
    }
    public DamageConfig(int schemaVersion, boolean fallEnabled, int fallColor, double fallSize,
            int fallX, int fallY, boolean hitEnabled, int hitColor, double hitSize, int hitX, int hitY, int hitSeconds, boolean calculatedDamage, String fallTemplate, String hitTemplate, double fallThreshold,
            ColorScale fallColors, ColorScale hitColors, boolean maceEnabled, boolean spearEnabled,
            boolean swordAxeEnabled, boolean useEnemyGear, boolean boldCriticalDamage) {
        this(2,fallEnabled,fallColor,fallSize,fallX,fallY,hitEnabled,hitColor,hitSize,hitX,hitY,hitSeconds,calculatedDamage,fallTemplate,hitTemplate,fallThreshold,fallColors,hitColors,maceEnabled,spearEnabled,swordAxeEnabled,useEnemyGear,boldCriticalDamage,true);
    }
    public DamageConfig(int schemaVersion, boolean fallEnabled, int fallColor, double fallSize,
            int fallX, int fallY, boolean hitEnabled, int hitColor, double hitSize, int hitX, int hitY,
            int hitSeconds, boolean calculatedDamage, String fallTemplate, String hitTemplate,
            double fallThreshold, ColorScale fallColors, ColorScale hitColors, boolean maceEnabled,
            boolean spearEnabled, boolean swordAxeEnabled, boolean useEnemyGear) {
        this(2,fallEnabled,fallColor,fallSize,fallX,fallY,hitEnabled,hitColor,hitSize,hitX,hitY,
                hitSeconds,calculatedDamage,fallTemplate,hitTemplate,fallThreshold,fallColors,hitColors,
                maceEnabled,spearEnabled,swordAxeEnabled,useEnemyGear,true,true);
    }
    public DamageConfig(int schemaVersion, boolean fallEnabled, int fallColor, double fallSize,
            int fallX, int fallY, boolean hitEnabled, int hitColor, double hitSize, int hitX, int hitY,
            int hitSeconds, boolean calculatedDamage, String fallTemplate, String hitTemplate,
            double fallThreshold, ColorScale fallColors, ColorScale hitColors) {
        this(2, fallEnabled, fallColor, fallSize, fallX, fallY, hitEnabled, hitColor, hitSize,
                hitX, hitY, hitSeconds, calculatedDamage, fallTemplate, hitTemplate, fallThreshold,
                fallColors, hitColors, true, true, false, false, true, true);
    }
    public DamageConfig(int schemaVersion, boolean fallEnabled, int fallColor, double fallSize,
            int fallX, int fallY, boolean hitEnabled, int hitColor, double hitSize, int hitX, int hitY, int hitSeconds, boolean calculatedDamage) {
        this(2, fallEnabled, fallColor, fallSize, fallX, fallY, hitEnabled, hitColor, hitSize, hitX, hitY, hitSeconds, calculatedDamage, "{blocks} blocks", "{damage} damage");
    }
    public DamageConfig(int schemaVersion, boolean fallEnabled, int fallColor, double fallSize,
            int fallX, int fallY, boolean hitEnabled, int hitColor, double hitSize, int hitX, int hitY, int hitSeconds) {
        this(2, fallEnabled, fallColor, fallSize, fallX, fallY, hitEnabled, hitColor, hitSize, hitX, hitY, hitSeconds, false);
    }
    public DamageConfig(int schemaVersion, boolean fallEnabled, int fallColor, double fallSize,
            int fallX, int fallY, boolean hitEnabled, int hitColor, double hitSize, int hitX, int hitY,
            int hitSeconds, boolean calculatedDamage, String fallTemplate, String hitTemplate) {
        this(2, fallEnabled, fallColor, fallSize, fallX, fallY, hitEnabled, hitColor, hitSize,
                hitX, hitY, hitSeconds, calculatedDamage, fallTemplate, hitTemplate, 1.5);
    }
    public DamageConfig(int schemaVersion, boolean fallEnabled, int fallColor, double fallSize,
            int fallX, int fallY, boolean hitEnabled, int hitColor, double hitSize, int hitX, int hitY,
            int hitSeconds, boolean calculatedDamage, String fallTemplate, String hitTemplate, double fallThreshold) {
        this(2,fallEnabled,fallColor,fallSize,fallX,fallY,hitEnabled,hitColor,hitSize,hitX,hitY,
                hitSeconds,calculatedDamage,fallTemplate,hitTemplate,fallThreshold,ColorScale.flat(0xffffff),ColorScale.damageDefault());
    }
    public static DamageConfig defaults() { return new DamageConfig(2, true, 0xffffff, 1, 0, 14, true, 0xff6666, 1, 0, 28, 3); }
    public DamageConfig validated() {
        if (schemaVersion != 2) throw new IllegalArgumentException("Unsupported configuration version");
        return new DamageConfig(2, fallEnabled, clamp(fallColor, 0, 0xffffff), size(fallSize),
                clamp(fallX, -2000, 2000), clamp(fallY, -2000, 2000), hitEnabled,
                clamp(hitColor, 0, 0xffffff), size(hitSize), clamp(hitX, -2000, 2000), clamp(hitY, -2000, 2000), clamp(hitSeconds, 1, 10), calculatedDamage, template(fallTemplate, false), template(hitTemplate, true), threshold(fallThreshold),
                (fallColors==null?ColorScale.flat(0xffffff):fallColors).validated(),(hitColors==null?ColorScale.damageDefault():hitColors).validated(),
                maceEnabled, spearEnabled, swordAxeEnabled, useEnemyGear, boldCriticalDamage, enabled);
    }
    private static String template(String s, boolean hit) { return DamageText.error(s, hit).isEmpty() && s.length() <= 160 ? s : hit ? "{damage} damage" : "{blocks} blocks"; }
    private static int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }
    private static double size(double n) { return Double.isFinite(n) ? Math.max(.5, Math.min(4, n)) : 1; }
    private static double threshold(double n) { return Double.isFinite(n) ? Math.max(0, Math.min(100, n)) : 1.5; }
}
