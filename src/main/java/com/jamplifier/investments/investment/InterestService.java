package com.jamplifier.investments.investment;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.util.ConfigKeys;
import com.jamplifier.investments.util.FoliaSchedulerUtil;
import org.bukkit.Bukkit;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class InterestService {

    private final InvestmentsPlugin plugin;
    private final InvestmentManager investmentManager;

    private BigDecimal ratePercent;
    private long intervalTicks;

    public InterestService(InvestmentsPlugin plugin, InvestmentManager investmentManager) {
        this.plugin = plugin;
        this.investmentManager = investmentManager;
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        double rate = plugin.getConfig().getDouble(ConfigKeys.INTEREST_RATE_PERCENT, 1.0D);
        int minutes = plugin.getConfig().getInt(ConfigKeys.INTEREST_INTERVAL_MINUTES, 10);

        this.ratePercent = BigDecimal.valueOf(rate);
        // minutes -> ticks
        this.intervalTicks = Math.max(1L, minutes * 60L * 20L);
    }

    public void start() {
        // Don't start if nonsense
        if (ratePercent.compareTo(BigDecimal.ZERO) <= 0 || intervalTicks <= 0) {
            plugin.getLogger().warning("[Investments] Interest disabled (rate-percent <= 0 or invalid interval).");
            return;
        }

        plugin.getLogger().info("[Investments] Starting interest task: rate=" + ratePercent + "% every " +
                (intervalTicks / 20L) + " seconds.");

        if (FoliaSchedulerUtil.isFolia()) {
            // Folia: use global region scheduler at fixed rate
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                    plugin,
                    scheduledTask -> tick(),
                    intervalTicks,
                    intervalTicks
            );
        } else {
            // Normal Paper/Spigot
            Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    this::tick,
                    intervalTicks,
                    intervalTicks
            );
        }
    }

    /** Apply interest to all loaded profiles. */
    private void tick() {
        // ratePercent is like "1.0" meaning 1% per cycle
        BigDecimal hundred = BigDecimal.valueOf(100);

        for (InvestmentProfile profile : investmentManager.getLoadedProfiles()) {
            boolean changed = false;

            for (Investment inv : profile.getInvestments()) {
                if (inv.getInvested().compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal interest = inv.getInvested()
                        .multiply(ratePercent)
                        .divide(hundred, 2, RoundingMode.DOWN); // 2 decimal places

                if (interest.compareTo(BigDecimal.ZERO) > 0) {
                    inv.addProfit(interest);
                    changed = true;
                }
            }

            if (changed) {
                investmentManager.saveProfile(profile);
            }
        }
    }
}
