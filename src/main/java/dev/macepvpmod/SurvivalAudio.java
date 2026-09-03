package dev.macepvpmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

final class SurvivalAudio {
    private static final SurvivalAudioSequence SEQUENCE = new SurvivalAudioSequence();
    private static final SimpleSoundInstance[] playing = new SimpleSoundInstance[2];
    private static Object player, level;
    private SurvivalAudio() {}

    static void tick(Minecraft mc) {
        if (player != mc.player || level != mc.level) {
            stop(mc); player = mc.player; level = mc.level;
        }
        var p = mc.player;
        var c = MacePvPMod.SURVIVAL_CONFIG.current();
        boolean active = p != null && mc.level != null && !mc.isPaused() && p.isAlive() && !p.isSpectator()
                && c.healingEnabled() && (c.harpVolume() > 0 || c.bassVolume() > 0)
                && (SurvivalState.healingState(p.getHealth(), p.getMaxHealth(), p.getFoodData().getSaturationLevel(), c) & 1) != 0;
        if (!active) { stop(mc); return; }
        var cue = SEQUENCE.tick(true, 1, c.audioStartInterval(), c.audioEndInterval(),
                SurvivalAudioSequence.severity(p.getHealth(), p.getMaxHealth(), c.healthPercent()));
        if (cue == null) return;
        int index = cue.bass() ? 1 : 0;
        if (playing[index] != null) mc.getSoundManager().stop(playing[index]);
        double volume = cue.bass() ? c.bassVolume() : c.harpVolume();
        if (volume <= 0) return;
        double basePitch = cue.bass() ? c.bassPitch() : c.harpPitch();
        float pitch = SurvivalAudioSequence.variedPitch(basePitch, p.getRandom().nextDouble());
        playing[index] = SimpleSoundInstance.forUI(
                (cue.bass() ? SoundEvents.NOTE_BLOCK_BASS : SoundEvents.NOTE_BLOCK_HARP).value(), pitch, (float)(cue.volume() * volume));
        mc.getSoundManager().play(playing[index]);
    }
    private static void stop(Minecraft mc) {
        SEQUENCE.reset();
        for (int i = 0; i < playing.length; i++) {
            if (playing[i] != null) mc.getSoundManager().stop(playing[i]);
            playing[i] = null;
        }
    }
}
