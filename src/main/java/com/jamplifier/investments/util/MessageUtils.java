package com.jamplifier.investments.util;

import com.jamplifier.investments.InvestmentsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Map;

public final class MessageUtils {

    private static InvestmentsPlugin plugin;
    private static FileConfiguration messages;

    private MessageUtils() {
    }

    public static void init(InvestmentsPlugin pl) {
        plugin = pl;
        load();
    }

    public static void reload() {
        load();
    }

    private static void load() {
        if (plugin == null) return;

        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(file);
    }

    public static String color(String input) {
        if (input == null) return "";
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private static String getRaw(String key) {
        if (messages == null) return "";
        return messages.getString(key, "");
    }

    public static void send(CommandSender sender, String key) {
        send(sender, key, null);
    }

    public static void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender == null) return;

        String raw = getRaw(key);
        if (raw == null || raw.isEmpty()) {
            return;
        }

        String prefix = messages.getString("prefix", "");
        String result = raw.replace("<prefix>", prefix);

        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                result = result.replace("%" + e.getKey() + "%", e.getValue());
            }
        }

        // support \n as newline
        for (String line : result.split("\\\\n")) {
            sender.sendMessage(color(line));
        }
    }
}
