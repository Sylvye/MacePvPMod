package dev.macepvpmod;

final class SpearDamageMath {
    private SpearDamageMath() {}
    static double relative(double attackerForward,double targetForward){return Math.max(0,attackerForward-targetForward);}
    static double raw(double baseDamage,double relativeSpeed,double multiplier){return baseDamage+Math.floor(Math.max(0,relativeSpeed)*multiplier);}
}
