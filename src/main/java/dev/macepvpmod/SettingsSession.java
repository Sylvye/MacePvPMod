package dev.macepvpmod;

import java.io.IOException;
import java.lang.reflect.RecordComponent;

/** One isolated draft spanning every settings file. */
final class SettingsSession {
    private final PitchConfig originalPitch;
    private final DamageConfig originalDamage;
    private final AttributeSwapConfig originalSwap;
    private final SurvivalConfig originalSurvival;
    private final HudConfig originalHud;
    private final ReachOutlineConfig originalReach;
    PitchConfig pitch;
    DamageConfig damage;
    AttributeSwapConfig swap;
    SurvivalConfig survival;
    HudConfig hud;
    ReachOutlineConfig reach;

    SettingsSession() {
        originalPitch = pitch = MacePvPMod.CONFIG.current();
        originalDamage = damage = MacePvPMod.DAMAGE_CONFIG.current();
        originalSwap = swap = MacePvPMod.ATTRIBUTE_SWAP_CONFIG.current();
        originalSurvival = survival = MacePvPMod.SURVIVAL_CONFIG.current();
        originalHud = hud = MacePvPMod.HUD_CONFIG.current();
        originalReach = reach = MacePvPMod.REACH_OUTLINE_CONFIG.current();
    }

    boolean dirty() {
        return !pitch.equals(originalPitch) || !damage.equals(originalDamage) || !swap.equals(originalSwap)
                || !survival.equals(originalSurvival) || !hud.equals(originalHud) || !reach.equals(originalReach);
    }

    String validation() {
        try {
            pitch.validated(); damage.validated(); swap.validated(); survival.validated(); hud.validated(); reach.validated();
            String fall = DamageText.error(damage.fallTemplate(), false);
            if (!fall.isEmpty()) return "Damage Counter: " + fall;
            String hit = DamageText.error(damage.hitTemplate(), true);
            if (!hit.isEmpty()) return "Damage Counter: " + hit;
            if (survival.audioEndInterval() > survival.audioStartInterval()) return "Survival Instincts: critical gap exceeds threshold gap.";
            return "";
        } catch (IllegalArgumentException error) { return error.getMessage(); }
    }

    void apply() throws IOException {
        String error = validation();
        if (!error.isEmpty()) throw new IOException(error);
        try {
            MacePvPMod.CONFIG.save(pitch);
            MacePvPMod.DAMAGE_CONFIG.save(damage);
            MacePvPMod.ATTRIBUTE_SWAP_CONFIG.save(swap);
            MacePvPMod.SURVIVAL_CONFIG.save(survival);
            MacePvPMod.HUD_CONFIG.save(hud);
            MacePvPMod.REACH_OUTLINE_CONFIG.save(reach);
        } catch (IOException failure) {
            // Restore both disk and live state when any later store fails.
            try { MacePvPMod.CONFIG.save(originalPitch); } catch (IOException ignored) {}
            try { MacePvPMod.DAMAGE_CONFIG.save(originalDamage); } catch (IOException ignored) {}
            try { MacePvPMod.ATTRIBUTE_SWAP_CONFIG.save(originalSwap); } catch (IOException ignored) {}
            try { MacePvPMod.SURVIVAL_CONFIG.save(originalSurvival); } catch (IOException ignored) {}
            try { MacePvPMod.HUD_CONFIG.save(originalHud); } catch (IOException ignored) {}
            try { MacePvPMod.REACH_OUTLINE_CONFIG.save(originalReach); } catch (IOException ignored) {}
            throw failure;
        }
    }

    void discard() {
        // Draft-only session: nothing has reached a store yet.
    }

    @SuppressWarnings("unchecked")
    private static <T extends Record> T with(T record,String field,Object value) {
        try {
            RecordComponent[] parts=record.getClass().getRecordComponents();Object[] values=new Object[parts.length];Class<?>[] types=new Class<?>[parts.length];boolean found=false;
            for(int i=0;i<parts.length;i++){types[i]=parts[i].getType();values[i]=parts[i].getAccessor().invoke(record);if(parts[i].getName().equals(field)){values[i]=value;found=true;}}
            if(!found)throw new IllegalArgumentException("Unknown setting: "+field);
            return (T)record.getClass().getDeclaredConstructor(types).newInstance(values);
        } catch(ReflectiveOperationException e){throw new IllegalStateException("Could not update "+field,e);}
    }
    void pitch(String field,Object value){pitch=with(pitch,field,value);}
    void damage(String field,Object value){damage=with(damage,field,value);}
    void swap(String field,Object value){swap=with(swap,field,value);}
    void survival(String field,Object value){survival=with(survival,field,value);}
    void reach(String field,Object value){reach=with(reach,field,value);}

    void resetAll() {
        pitch = PitchConfig.defaults(); damage = DamageConfig.defaults(); swap = AttributeSwapConfig.defaults();
        survival = SurvivalConfig.defaults(); hud = HudConfig.defaults(); reach = ReachOutlineConfig.defaults();
    }

    void toggle(int page) {
        if (page == 1) pitch = new PitchConfig(1,!pitch.enabled(),pitch.width(),pitch.thickness(),pitch.color(),pitch.opacity(),pitch.targetPitch(),pitch.sensitivity(),pitch.maxDisplacement(),pitch.thirdPerson());
        if (page == 2) damage = copyDamage(!damage.enabled());
        if (page == 3) swap = new AttributeSwapConfig(2,swap.visualEnabled(),swap.soundEnabled(),swap.soundId(),swap.weaponOnly(),swap.successfulHitOnly(),!swap.enabled());
        if (page == 4) survival = copySurvival(!survival.enabled());
        if (page == 5) reach = new ReachOutlineConfig(1,!reach.enabled(),reach.color(),reach.intensity(),reach.thickness(),reach.topFacesOnly(),reach.hardToReachMode(),reach.minimumReachableArea());
    }

    boolean enabled(int page) { return switch(page) { case 1 -> pitch.enabled(); case 2 -> damage.enabled(); case 3 -> swap.enabled(); case 4 -> survival.enabled(); case 5 -> reach.enabled(); default -> true; }; }

    void reset(int page) {
        switch (page) { case 1 -> pitch=PitchConfig.defaults(); case 2 -> damage=DamageConfig.defaults(); case 3 -> swap=AttributeSwapConfig.defaults(); case 4 -> survival=SurvivalConfig.defaults(); case 5 -> reach=ReachOutlineConfig.defaults(); case 6 -> hud=HudConfig.defaults(); default -> {} }
    }

    private DamageConfig copyDamage(boolean enabled) { return copyDamage(damage,enabled); }
    private DamageConfig copyDamage(DamageConfig d,boolean enabled) {
        return new DamageConfig(2,d.fallEnabled(),d.fallColor(),d.fallSize(),d.fallX(),d.fallY(),d.hitEnabled(),d.hitColor(),d.hitSize(),d.hitX(),d.hitY(),d.hitSeconds(),d.calculatedDamage(),d.fallTemplate(),d.hitTemplate(),d.fallThreshold(),d.fallColors(),d.hitColors(),d.maceEnabled(),d.spearEnabled(),d.swordAxeEnabled(),d.useEnemyGear(),d.boldCriticalDamage(),enabled);
    }
    private SurvivalConfig copySurvival(boolean enabled) { return copySurvival(survival,enabled); }
    private SurvivalConfig copySurvival(SurvivalConfig s,boolean enabled) {
        return new SurvivalConfig(2,s.retotemEnabled(),s.healingEnabled(),s.retotemText(),s.retotemColor(),s.retotemSize(),s.retotemX(),s.retotemY(),s.healthText(),s.healthColor(),s.saturationText(),s.saturationColor(),s.combinedText(),s.combinedColor(),s.healingSize(),s.healingX(),s.healingY(),s.healthPercent(),s.saturationThreshold(),s.harpVolume(),s.bassVolume(),s.harpPitch(),s.bassPitch(),s.audioStartInterval(),s.audioEndInterval(),s.healingItems(),s.saturationItems(),s.sounds(),enabled);
    }
}
