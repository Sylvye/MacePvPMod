package dev.macepvpmod;

final class SurvivalState {
    private SurvivalState() {}
    static boolean needsRetotem(boolean hasTotem, boolean emptyOffhand) {
        return hasTotem && emptyOffhand;
    }

    static int actionableState(int state, boolean healingItem, boolean saturationItem) {
        return ((state & 1) != 0 && (healingItem || saturationItem) ? 1 : 0)
                | ((state & 2) != 0 && saturationItem ? 2 : 0);
    }

    // 0 = healthy, 1 = health, 2 = saturation, 3 = both.
    static int healingState(double health, double maxHealth, double saturation, SurvivalConfig c) {
        boolean lowHealth = maxHealth > 0 && health <= maxHealth * c.healthPercent() / 100;
        boolean lowSaturation = saturation <= c.saturationThreshold();
        return (lowHealth ? 1 : 0) | (lowSaturation ? 2 : 0);
    }

}
