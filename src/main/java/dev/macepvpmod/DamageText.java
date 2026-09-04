package dev.macepvpmod;
import java.util.Locale;
final class DamageText {
    static String error(String text, boolean hit) {
        if (text == null || text.isBlank()) return "Enter a message.";
        String rest = text.replace("{blocks}", "");
        if (hit) rest = rest.replace("{damage}", "");
        return rest.contains("{") || rest.contains("}") ? "Supported variables: {blocks}" + (hit ? ", {damage}" : "") : "";
    }
    static String format(String text, double blocks, double damage) {
        return text.replace("{blocks}", String.format(Locale.ROOT, "%.1f", blocks))
                .replace("{damage}", String.format(Locale.ROOT, "%.1f", damage));
    }
}
