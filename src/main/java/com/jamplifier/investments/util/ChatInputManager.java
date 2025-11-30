package com.jamplifier.investments.util;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class ChatInputManager implements Listener {

    private final Map<UUID, BiConsumer<Player, String>> waiting = new ConcurrentHashMap<>();

    public void await(Player player, BiConsumer<Player, String> handler) {
        waiting.put(player.getUniqueId(), handler);
        MessageUtils.send(player, "chat-enter-amount.start");
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

        event.setCancelled(true);
        String msg = event.getMessage().trim();

        if (msg.equalsIgnoreCase("cancel")) {
            waiting.remove(player.getUniqueId());
            MessageUtils.send(player, "chat-enter-amount.cancelled");
            return;
        }

        waiting.remove(player.getUniqueId());
        handler.accept(player, msg);
    }
}
