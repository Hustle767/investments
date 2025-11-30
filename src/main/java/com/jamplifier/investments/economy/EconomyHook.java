package com.jamplifier.investments.economy;

import com.jamplifier.investments.InvestmentsPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyHook {

    private final InvestmentsPlugin plugin;
    private Economy economy;

    public EconomyHook(InvestmentsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts to hook into Vault's Economy service.
     *
     * @return true if successful, false otherwise
     */
    public boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("[Investments] Vault plugin not found.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            plugin.getLogger().severe("[Investments] Vault economy provider not found.");
            return false;
        }

        economy = rsp.getProvider();
        if (economy == null) {
            plugin.getLogger().severe("[Investments] Vault economy provider is null.");
            return false;
        }

        plugin.getLogger().info("[Investments] Hooked into Vault economy: " + economy.getName());
        return true;
    }

    public Economy getEconomy() {
        return economy;
    }
}
