package dev.macepvpmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

final class SurvivalAudio {
    private static final SurvivalAudioSequence SEQUENCE = new SurvivalAudioSequence();
    private static final java.util.List<SimpleSoundInstance> playing = new java.util.ArrayList<>();
    private static final SoundPlaylistCursor CURSOR = new SoundPlaylistCursor();
    private static java.util.List<SoundEntry> playlist = java.util.List.of();
    private static Object player, level;
    private SurvivalAudio() {}

    static void tick(Minecraft mc) {
        if (player != mc.player || level != mc.level) {
            stop(mc); player = mc.player; level = mc.level;
        }
        var p = mc.player;
        var c = MacePvPMod.SURVIVAL_CONFIG.current();
        if (!playlist.equals(c.sounds())) { stop(mc); playlist = c.sounds(); }
        boolean active = p != null && mc.level != null && !mc.isPaused() && p.isAlive() && !p.isSpectator()
                && c.healingEnabled() && c.sounds().stream().anyMatch(e -> e.volume() > 0)
                && (SurvivalState.healingState(p.getHealth(), p.getMaxHealth(), p.getFoodData().getSaturationLevel(), c) & 1) != 0;
        if (!active) { stop(mc); return; }
        var cue = SEQUENCE.tick(true, 1, c.audioStartInterval(), c.audioEndInterval(),
                SurvivalAudioSequence.severity(p.getHealth(), p.getMaxHealth(), c.healthPercent()));
        if (cue == null) return;
        if (playlist.isEmpty()) return;
        var entry = playlist.get(CURSOR.next(playlist.size()));
        var id = net.minecraft.resources.Identifier.tryParse(entry.sound());
        if (id == null || !net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(id) || entry.volume() <= 0) return;
        playing.removeIf(sound -> !mc.getSoundManager().isActive(sound));
        var sound = SimpleSoundInstance.forUI(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(id),
                SurvivalAudioSequence.variedPitch(entry.pitch(), p.getRandom().nextDouble()), (float)(cue.volume() * entry.volume()));
        // Bound overlap even for long sound events.
        if (playing.size() >= playlist.size()) mc.getSoundManager().stop(playing.removeFirst());
        playing.add(sound); mc.getSoundManager().play(sound);
    }

    private static void stop(Minecraft mc) {
        SEQUENCE.reset();
        CURSOR.reset();
        for (var sound : playing) mc.getSoundManager().stop(sound);
        playing.clear();
    }
}
