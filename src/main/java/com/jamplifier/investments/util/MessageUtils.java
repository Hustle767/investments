package com.jamplifier.investments.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public final class MessageUtils {

    private static JavaPlugin plugin;
    private static FileConfiguration messages;
    private static String prefix = "";

    private MessageUtils() {
    }

    public static void init(JavaPlugin pl) {
        plugin = pl;

        // Make sure file exists
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        reload();
    }

    public static void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        messages = YamlConfiguration.loadConfiguration(file);

        String rawPrefix = messages.getString("prefix", "");
        prefix = color(rawPrefix);
    }

    public static void send(CommandSender sender, String key) {
        send(sender, key, Collections.emptyMap());
    }

    public static void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String raw = messages.getString(key, "");
        if (raw == null || raw.isEmpty()) {
            return;
        }

        String msg = raw.replace("<prefix>", prefix);

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String ph = "%" + entry.getKey() + "%";
                msg = msg.replace(ph, entry.getValue());
            }
        }

        sender.sendMessage(color(msg));
    }

    public static String format(String key, Map<String, String> placeholders) {
        String raw = messages.getString(key, "");
        if (raw == null) raw = "";

        String msg = raw.replace("<prefix>", prefix);

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String ph = "%" + entry.getKey() + "%";
                msg = msg.replace(ph, entry.getValue());
            }
        }

        return color(msg);
    }

    public static String getPrefix() {
        return prefix;
    }

    public static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
