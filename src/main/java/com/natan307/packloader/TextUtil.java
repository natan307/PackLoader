package com.natan307.packloader;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtil {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private TextUtil() {
    }

    /** Translates &amp; colour codes and &amp;#RRGGBB hex colours into section-sign codes. */
    public static String color(String input) {
        if (input == null) {
            return "";
        }
        Matcher m = HEX.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            StringBuilder rep = new StringBuilder();
            rep.append(ChatColor.COLOR_CHAR).append('x');
            for (char c : m.group(1).toCharArray()) {
                rep.append(ChatColor.COLOR_CHAR).append(c);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep.toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
