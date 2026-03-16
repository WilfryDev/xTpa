package es.xplugins.xtpa.utils;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    // #RRGGBB y &#RRGGBB
    private static final Pattern HEX_PATTERN = Pattern.compile("&#[a-fA-F0-9]{6}|#[a-fA-F0-9]{6}");

    public static String colorize(String message) {

        if (message == null) return "";

        Matcher matcher = HEX_PATTERN.matcher(message);

        while (matcher.find()) {

            String hexCode = message.substring(matcher.start(), matcher.end());
            String replaceSharp = hexCode.replace("&#", "#");
            message = message.replace(hexCode, ChatColor.of(replaceSharp) + "");
            matcher = HEX_PATTERN.matcher(message);
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }
}