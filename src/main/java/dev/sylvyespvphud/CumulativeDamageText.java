package dev.sylvyespvphud;

import java.util.Locale;

final class CumulativeDamageText {
    private CumulativeDamageText() {}

    static String format(String template,String player,double damage) {
        return template.replace("{player}",player)
                .replace("{damage}",String.format(Locale.ROOT,"%.1f",damage));
    }

    static String error(String template) {
        if(template==null||template.isBlank())return "Enter a message.";
        String rest=template.replace("{player}","").replace("{damage}","");
        return rest.contains("{")||rest.contains("}")?"Supported variables: {player}, {damage}":"";
    }
}
