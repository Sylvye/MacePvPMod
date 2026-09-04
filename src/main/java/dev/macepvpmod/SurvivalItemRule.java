package dev.macepvpmod;

import java.util.*;

/** Registry IDs only, so configuration can load before game registries initialize. */
public record SurvivalItemRule(String item, String potion) {
    public SurvivalItemRule {
        if (!validId(item) || (potion != null && !validId(potion))) throw new IllegalArgumentException("Invalid item rule");
    }
    private static boolean validId(String id) { return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"); }
    public static List<SurvivalItemRule> healingDefaults() {
        var rules = new ArrayList<SurvivalItemRule>();
        for (String form : List.of("potion", "splash_potion", "lingering_potion"))
            for (String effect : List.of("healing", "strong_healing", "regeneration", "long_regeneration", "strong_regeneration"))
                rules.add(new SurvivalItemRule("minecraft:" + form, "minecraft:" + effect));
        return List.copyOf(rules);
    }
    public static List<SurvivalItemRule> saturationDefaults() {
        return List.of("cooked_beef", "cooked_porkchop", "cooked_mutton", "cooked_chicken", "golden_carrot", "golden_apple", "enchanted_golden_apple")
                .stream().map(id -> new SurvivalItemRule("minecraft:" + id, null)).toList();
    }
    static List<SurvivalItemRule> validated(List<SurvivalItemRule> rules, List<SurvivalItemRule> fallback) {
        if (rules == null) return fallback;
        return List.copyOf(new LinkedHashSet<>(rules));
    }
}
