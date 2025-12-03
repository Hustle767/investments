package com.jamplifier.investments.investment;

import com.jamplifier.investments.InvestmentsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class MaxInvestPermissionService {

    private static final String MAX_AMOUNT_PERMISSION_PREFIX = "invest.maxlimit.";

    private final InvestmentsPlugin plugin;
    private final Map<String, Integer> limits = new HashMap<>();

    public MaxInvestPermissionService(InvestmentsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        limits.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("max-invest-permissions");
        if (sec == null) {
            return;
        }

        for (String perm : sec.getKeys(false)) {
            int value = sec.getInt(perm, 0);
            if (value > 0) {
                limits.put(perm, value);
            }
        }
    }

    /**
     * Max number of simultaneous investments based on config section
     * max-invest-permissions.
     */
    public int getMaxInvestments(Player player) {
        int max = 0;
        for (Map.Entry<String, Integer> e : limits.entrySet()) {
            if (player.hasPermission(e.getKey()) && e.getValue() > max) {
                max = e.getValue();
            }
        }
        return max;
    }

    /**
     * Returns the highest max total-invested amount granted by permissions
     * in the form "invest.maxlimit.<amount>".
     *
     * Example: invest.maxlimit.100000 -> player can have at most 100,000
     * invested in total.
     *
     * If the player has no such permission, returns BigDecimal.ZERO.
     */
    public BigDecimal getMaxTotalAmount(Player player) {
        BigDecimal max = BigDecimal.ZERO;

        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }

            String perm = info.getPermission();
            if (!perm.startsWith(MAX_AMOUNT_PERMISSION_PREFIX)) {
                continue;
            }

            String suffix = perm.substring(MAX_AMOUNT_PERMISSION_PREFIX.length());
            if (suffix.isEmpty()) {
                continue;
            }

            try {
                // We expect an integer amount, e.g. invest.maxlimit.100000
                BigDecimal value = new BigDecimal(suffix);
                if (value.compareTo(max) > 0) {
                    max = value;
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed nodes like invest.maxlimit.foo
            }
        }

        return max;
    }
}
