package dev.macepvpmod;

import java.util.Locale;

final class VectorsText {
    private VectorsText() {}
    static String error(String text) {
        if (text == null || text.isBlank()) return "Enter a velocity message.";
        if (!text.contains("{magnitude}")) return "Velocity message must include {magnitude}.";
        String rest = text.replace("{magnitude}", "");
        return rest.contains("{") || rest.contains("}") ? "Supported variable: {magnitude}" : "";
    }
    static String format(String text, double magnitude) {
        return text.replace("{magnitude}", String.format(Locale.ROOT, "%.1f", magnitude));
    }
}
