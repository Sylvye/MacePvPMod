package dev.sylvyespvphud;

/** Complete, shareable settings payload owned by one profile. */
public record ProfileSettings(PitchConfig pitch, DamageConfig damage, AttributeSwapConfig attributeSwaps,
                              SurvivalConfig survival, HudConfig hud, ReachOutlineConfig reachOutlines,
                              VectorsConfig vectors, TrackerConfig playerTracker) {
    public static ProfileSettings defaults() {
        return new ProfileSettings(PitchConfig.defaults(), DamageConfig.defaults(), AttributeSwapConfig.defaults(),
                SurvivalConfig.defaults(), HudConfig.defaults(), ReachOutlineConfig.defaults(),
                VectorsConfig.defaults(), TrackerConfig.defaults());
    }

    static ProfileSettings current() {
        return new ProfileSettings(SylvyesPvPHud.CONFIG.current(), SylvyesPvPHud.DAMAGE_CONFIG.current(),
                SylvyesPvPHud.ATTRIBUTE_SWAP_CONFIG.current(), SylvyesPvPHud.SURVIVAL_CONFIG.current(),
                SylvyesPvPHud.HUD_CONFIG.current(), SylvyesPvPHud.REACH_OUTLINE_CONFIG.current(),
                SylvyesPvPHud.VECTORS_CONFIG.current(), SylvyesPvPHud.TRACKER_CONFIG.current());
    }

    public ProfileSettings validated() {
        if (pitch == null || damage == null || attributeSwaps == null || survival == null || hud == null
                || reachOutlines == null || vectors == null || playerTracker == null)
            throw new IllegalArgumentException("A profile is missing settings.");
        return new ProfileSettings(pitch.validated(), damage.validated(), attributeSwaps.validated(),
                survival.validated(), hud.validated(), reachOutlines.validated(), vectors.validated(),
                playerTracker.validated());
    }

    void activate() {
        ProfileSettings valid = validated();
        SylvyesPvPHud.CONFIG.activate(valid.pitch());
        SylvyesPvPHud.DAMAGE_CONFIG.activate(valid.damage());
        SylvyesPvPHud.ATTRIBUTE_SWAP_CONFIG.activate(valid.attributeSwaps());
        SylvyesPvPHud.SURVIVAL_CONFIG.activate(valid.survival());
        SylvyesPvPHud.HUD_CONFIG.activate(valid.hud());
        SylvyesPvPHud.REACH_OUTLINE_CONFIG.activate(valid.reachOutlines());
        SylvyesPvPHud.VECTORS_CONFIG.activate(valid.vectors());
        SylvyesPvPHud.TRACKER_CONFIG.activate(valid.playerTracker());
    }
}
