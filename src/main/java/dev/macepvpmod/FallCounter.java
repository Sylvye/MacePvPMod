package dev.macepvpmod;

import net.minecraft.client.player.LocalPlayer;

/** HUD-only distance: never changes Minecraft's damage accumulator. */
public final class FallCounter {
    private static final FallDistanceTracker TRACKER = new FallDistanceTracker();
    private static LocalPlayer owner;
    private FallCounter() {}
    private static boolean resetRequired(LocalPlayer player) {
        return player == null || !player.isAlive() || player.onGround() || player.isInWater()
                || player.isInLava() || player.onClimbable() || player.isPassenger()
                || player.isSpectator() || player.getAbilities().flying;
    }
    public static void tick(LocalPlayer player) {
        if (owner != player || resetRequired(player)) TRACKER.reset();
        owner = player;
    }
    public static void moved(LocalPlayer player, double displacement) {
        tick(player);
        TRACKER.move(displacement, resetRequired(player));
    }
    public static double distance(LocalPlayer player) {
        return owner == player && !resetRequired(player) ? TRACKER.distance() : 0;
    }
    public static void reset() { TRACKER.reset(); }
}
