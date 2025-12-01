package com.jamplifier.investments.util;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple per-player GUI click cooldown to prevent spam/exploits.
 */
public final class GuiClickGuard {

    // How long between allowed clicks (ms)
    private static final long COOLDOWN_MS = 200L; // ~4 ticks

    private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();

    private GuiClickGuard() {
    }

    /**
     * @return true if this click should be blocked (too soon after last), false if allowed.
     */
    public static boolean shouldBlock(Player player) {
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();

        Long last = LAST_CLICK.get(id);
        if (last != null && (now - last) < COOLDOWN_MS) {
            // too soon -> block
            return true;
        }

        // allow and update timestamp
        LAST_CLICK.put(id, now);
        return false;
    }

    /**
     * Optional: call on player quit if you want to clean map, not required.
     */
    public static void clear(Player player) {
        LAST_CLICK.remove(player.getUniqueId());
    }
}
