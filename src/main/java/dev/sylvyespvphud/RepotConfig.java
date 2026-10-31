package dev.sylvyespvphud;

import java.util.List;

public record RepotConfig(boolean enabled, List<String> effects, int thresholdSeconds,
                          int graceSeconds, boolean icons, int iconSpacing, String template, RepotAttention attention) {
    public RepotConfig(boolean enabled, List<String> effects, int thresholdSeconds,
                       int graceSeconds, boolean icons, int iconSpacing, String template) {
        this(enabled, effects, thresholdSeconds, graceSeconds, icons, iconSpacing, template, RepotAttention.defaults());
    }
    static RepotConfig defaults() {
        return new RepotConfig(true, List.of("minecraft:speed", "minecraft:strength",
                "minecraft:fire_resistance", "minecraft:weaving"), 15, 10, true, 4, "Repot {effect}");
    }

    public RepotConfig validated() {
        var selected = effects == null ? defaults().effects() : effects.stream()
                .filter(java.util.Objects::nonNull)
                .filter(id -> id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                .distinct().toList();
        String message = template == null ? defaults().template() : template;
        String error = textError(message);
        if (!error.isEmpty()) throw new IllegalArgumentException("Repot Warning: " + error);
        return new RepotConfig(enabled, selected, Math.clamp(thresholdSeconds, 0, 600),
                Math.clamp(graceSeconds, 0, 600), icons, Math.clamp(iconSpacing, 0, 20), message,
                attention == null ? RepotAttention.defaults() : attention.validated());
    }

    static String textError(String template) {
        if (template == null || template.isBlank()) return "Enter a nonempty message.";
        if (template.length() > 160) return "Message must be at most 160 characters.";
        if (!template.contains("{effect}")) return "Message must include {effect}.";
        String remainder = template.replace("{effect}", "");
        return remainder.contains("{") || remainder.contains("}") ? "Only {effect} is supported." : "";
    }

    String format(List<String> names) { return template.replace("{effect}", String.join(", ", names)); }
}
