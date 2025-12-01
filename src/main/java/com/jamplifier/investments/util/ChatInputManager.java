package com.jamplifier.investments.util;

import com.jamplifier.investments.InvestmentsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class ChatInputManager implements Listener {

    private final Map<UUID, BiConsumer<Player, String>> waiting = new ConcurrentHashMap<>();

    public void await(Player player, BiConsumer<Player, String> handler) {
        waiting.put(player.getUniqueId(), handler);

        // Build options string from config: pre-selected-investments:
        ConfigurationSection sec = InvestmentsPlugin.getInstance()
                .getConfig()
                .getConfigurationSection("pre-selected-investments");

        String optionsText = "none";
        if (sec != null && !sec.getKeys(false).isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (String key : new TreeSet<>(sec.getKeys(false))) { // sorted keys "1","2","3"
                double val = sec.getDouble(key, -1.0D);
                if (val <= 0) continue;

                BigDecimal amount = BigDecimal.valueOf(val);
                // 1 = 10k, 2 = 20k, 3 = 50k
                parts.add(key + " = " + AmountUtil.formatShort(amount));
            }
            if (!parts.isEmpty()) {
                optionsText = String.join(", ", parts);
            }
        }

        // Read minimum from config
        double minAmountDouble = InvestmentsPlugin.getInstance()
                .getConfig()
                .getDouble(ConfigKeys.MIN_INVEST_AMOUNT, 10000.0D);

        BigDecimal minAmount = BigDecimal.valueOf(minAmountDouble);

        Map<String, String> ph = new HashMap<>();
        ph.put("options", optionsText);
        // minimum 10k
        ph.put("min", AmountUtil.formatShort(minAmount));

        MessageUtils.send(player, "chat-enter-amount.start", ph);
    }

    public void cancel(Player player) {
        waiting.remove(player.getUniqueId());
        MessageUtils.send(player, "chat-enter-amount.cancelled");
    }

    public boolean isWaiting(Player player) {
        return waiting.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        BiConsumer<Player, String> handler = waiting.get(player.getUniqueId());
        if (handler == null) {
            return;
        }

        // Stop normal chat broadcast
        event.setCancelled(true);

        String msg = event.getMessage().trim();

        waiting.remove(player.getUniqueId());

        if (msg.equalsIgnoreCase("cancel")) {
            FoliaSchedulerUtil.runForEntity(player, () ->
                    MessageUtils.send(player, "chat-enter-amount.cancelled")
            );
            return;
        }

        // Run the handler on the correct thread (entity scheduler / main)
        FoliaSchedulerUtil.runForEntity(player, () -> handler.accept(player, msg));
    }
}
