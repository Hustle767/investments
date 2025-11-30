package com.jamplifier.investments.investment;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.util.ConfigKeys;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public class MaxInvestPermissionService {

    private final InvestmentsPlugin plugin;
    private final Map<String, Integer> limitsByPermission = new LinkedHashMap<>();

    public MaxInvestPermissionService(InvestmentsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        limitsByPermission.clear();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection(ConfigKeys.MAX_INVEST_PERMISSIONS);
        if (section == null) {
            return;
        }

        for (String perm : section.getKeys(false)) {
            int limit = section.getInt(perm, 0);
            limitsByPermission.put(perm, limit);
        }
    }

    /**
     * Returns the highest investment limit for this player based on permissions.
     * If the player has none of the listed permissions, returns 0.
     */
    public int getMaxInvestments(Player player) {
        int max = 0;
        for (Map.Entry<String, Integer> entry : limitsByPermission.entrySet()) {
            String perm = entry.getKey();
            int limit = entry.getValue();

            if (player.hasPermission(perm) && limit > max) {
                max = limit;
            }
        }
        return max;
    }
}
