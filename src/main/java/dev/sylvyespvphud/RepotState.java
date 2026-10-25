package dev.sylvyespvphud;

import java.util.*;

/** Tick-based expiry inference, independent of rendering and inventory availability. */
final class RepotState {
    record Effect(int ticks, boolean ambient, boolean infinite) {}
    private final Map<String, Effect> previous = new HashMap<>();
    private final Map<String, Integer> grace = new HashMap<>();

    void clear() { previous.clear(); grace.clear(); }

    List<String> tick(RepotConfig config, Map<String, Effect> active, Set<String> potions) {
        if (!config.enabled()) { clear(); return List.of(); }
        previous.keySet().retainAll(config.effects());
        grace.keySet().retainAll(config.effects());
        grace.replaceAll((id, ticks) -> ticks - 1);
        grace.values().removeIf(ticks -> ticks <= 0);
        List<String> warnings = new ArrayList<>();
        for (String id : config.effects()) {
            Effect now = active.get(id), before = previous.remove(id);
            boolean warn;
            if (now != null) {
                grace.remove(id);
                // Ambient effects are not remembered, so their removal cannot start grace.
                if (now.ambient() || now.infinite()) continue;
                previous.put(id, now);
                warn = config.thresholdSeconds() > 0 && now.ticks() < config.thresholdSeconds() * 20;
            } else {
                if (before != null && before.ticks() <= 1 && config.graceSeconds() > 0)
                    grace.put(id, config.graceSeconds() * 20);
                warn = config.graceSeconds() > 0 && grace.containsKey(id);
            }
            if (warn && potions.contains(id)) warnings.add(id);
        }
        return List.copyOf(warnings);
    }
}
