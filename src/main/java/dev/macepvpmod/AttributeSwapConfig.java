package dev.macepvpmod;

public record AttributeSwapConfig(int schemaVersion, boolean visualEnabled, boolean soundEnabled, String soundId,
                                  boolean weaponOnly, boolean successfulHitOnly, boolean enabled) {
    public AttributeSwapConfig(int schemaVersion, boolean visualEnabled, boolean soundEnabled, String soundId,
                               boolean weaponOnly, boolean successfulHitOnly) {
        this(2, visualEnabled, soundEnabled, soundId, weaponOnly, successfulHitOnly, true);
    }
    public static AttributeSwapConfig defaults() {
        return new AttributeSwapConfig(2, true, true, "minecraft:entity.experience_orb.pickup", true, true, true);
    }
    public AttributeSwapConfig validated() {
        if (schemaVersion != 2 || soundId == null || !soundId.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("Invalid attribute swap configuration");
        return this;
    }
}
