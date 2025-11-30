package com.jamplifier.investments.investment;

import com.jamplifier.investments.InvestmentsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class MaxInvestPermissionService {

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

    public int getMaxInvestments(Player player) {
        int max = 0;
        for (Map.Entry<String, Integer> e : limits.entrySet()) {
            if (player.hasPermission(e.getKey()) && e.getValue() > max) {
                max = e.getValue();
            }
        }
        return max;
    }
}
