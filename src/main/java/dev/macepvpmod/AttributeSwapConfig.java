package dev.macepvpmod;

public record AttributeSwapConfig(int schemaVersion, boolean visualEnabled, boolean soundEnabled, String soundId,
                                  boolean weaponOnly, boolean successfulHitOnly) {
    public static AttributeSwapConfig defaults() {
        return new AttributeSwapConfig(1, true, true, "minecraft:entity.experience_orb.pickup", true, true);
    }
    public AttributeSwapConfig validated() {
        if (schemaVersion != 1 || soundId == null || !soundId.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("Invalid attribute swap configuration");
        return this;
    }
}
