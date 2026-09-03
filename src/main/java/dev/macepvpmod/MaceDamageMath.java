package dev.macepvpmod;

/** Vanilla 26.2 raw damage; no target defenses or Breach armor piercing. */
public final class MaceDamageMath {
    private MaceDamageMath() {}
    public static double calculate(double attackDamage, double cooldown, double fallDistance,
                                   boolean fallFlying, int densityLevel, boolean criticalEligible) {
        double charge = Math.max(0, Math.min(1, cooldown));
        double damage = attackDamage * (0.2 + 0.8 * charge * charge);
        if (Double.isFinite(fallDistance) && fallDistance > 1.5 && !fallFlying) {
            double bonus = 4 * Math.min(fallDistance, 3)
                    + 2 * Math.min(Math.max(fallDistance - 3, 0), 5)
                    + Math.max(fallDistance - 8, 0);
            damage += bonus + 0.5 * Math.max(0, densityLevel) * fallDistance;
        }
        // Player.attack adds the entire smash bonus before multiplying critical damage.
        return damage * (charge > 0.9 && criticalEligible ? 1.5 : 1);
    }
}
