package dev.sylvyespvphud;

import java.util.*;

/** Tick-based expiry inference, independent of rendering and inventory availability. */
final class RepotState {
    record Effect(int ticks, boolean ambient, boolean infinite) {}
    record Warning(String id, int remainingTicks, boolean ended) {}
    private record Removal(long tick, boolean totem) {}
    private final Map<String, Effect> previous = new HashMap<>();
    private final Map<String, Integer> grace = new HashMap<>();
    private final Map<String, Removal> removals = new HashMap<>();
    private List<Warning> details = List.of();
    private long tick, popTick = -100;

    void clear() { previous.clear(); grace.clear(); removals.clear(); details = List.of(); tick = 0; popTick = -100; }

    List<Warning> details() { return details; }

    // Captured before vanilla handles the removal packet, even if the HUD has not ticked yet.
    void removed(String id, Effect effect) {
        if (effect == null || effect.ambient() || effect.infinite()) {
            removals.remove(id); previous.remove(id); grace.remove(id); return;
        }
        removals.put(id, new Removal(tick, tick - popTick <= 2));
    }

    void totemPopped() {
        popTick = tick;
        removals.replaceAll((id, removal) -> new Removal(removal.tick(), tick - removal.tick() <= 2));
    }

    List<String> tick(RepotConfig config, Map<String, Effect> active, Set<String> potions) {
        if (!config.enabled()) { clear(); return List.of(); }
        previous.keySet().retainAll(config.effects());
        grace.keySet().retainAll(config.effects());
        removals.keySet().retainAll(config.effects());
        removals.values().removeIf(removal -> tick - removal.tick() > 2);
        grace.replaceAll((id, ticks) -> ticks - 1);
        grace.values().removeIf(ticks -> ticks <= 0);
        List<String> warnings = new ArrayList<>();
        List<Warning> nextDetails = new ArrayList<>();
        for (String id : config.effects()) {
            Effect now = active.get(id), before = previous.remove(id);
            boolean warn;
            if (now != null) {
                grace.remove(id);
                removals.remove(id);
                // Ambient effects are not remembered, so their removal cannot start grace.
                if (now.ambient() || now.infinite()) continue;
                previous.put(id, now);
                warn = config.thresholdSeconds() > 0 && now.ticks() < config.thresholdSeconds() * 20;
            } else {
                Removal removal = removals.get(id);
                boolean totem = removal != null && removal.totem();
                if ((totem || before != null && before.ticks() <= 1) && config.graceSeconds() > 0)
                    grace.put(id, config.graceSeconds() * 20);
                if (totem) removals.remove(id);
                warn = config.graceSeconds() > 0 && grace.containsKey(id);
            }
            if (warn && potions.contains(id)) {
                warnings.add(id);
                nextDetails.add(new Warning(id, now == null ? 0 : now.ticks(), now == null));
            }
        }
        details = List.copyOf(nextDetails);
        tick++;
        return List.copyOf(warnings);
    }
}
