package dev.sylvyespvphud;

/** Optional as a whole in old configurations, so new visual defaults migrate intact. */
public record RepotAttention(boolean pulsing, double slowInterval, double fastInterval,
                             boolean endedColorEnabled, int endedColor) {
    static RepotAttention defaults() { return new RepotAttention(true, 1.5, .35, true, 0xff3333); }

    public RepotAttention validated() {
        double slow = HudStyle.limit(slowInterval, .1, 5, 1.5);
        double fast = HudStyle.limit(fastInterval, .1, 5, .35);
        if (fast > slow) throw new IllegalArgumentException("Repot Warning: fast pulse interval must not exceed slow interval.");
        return new RepotAttention(pulsing, slow, fast, endedColorEnabled, endedColor & 0xffffff);
    }

    double period(int remainingTicks, int thresholdSeconds) {
        double fraction = thresholdSeconds <= 0 ? 0 : Math.clamp(remainingTicks / (thresholdSeconds * 20.0), 0, 1);
        return fastInterval + (slowInterval - fastInterval) * fraction;
    }

    double opacity(double phase) { return pulsing ? .35 + .65 * (.5 + .5 * Math.cos(phase * Math.PI * 2)) : 1; }
    int color(boolean ended, int normal) { return ended && endedColorEnabled ? endedColor : normal; }
}
