package com.jamplifier.investments.investment;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.util.ConfigKeys;
import com.jamplifier.investments.util.FoliaSchedulerUtil;
import org.bukkit.Bukkit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InterestService {

    private final InvestmentsPlugin plugin;
    private final InvestmentManager investmentManager;

    private BigDecimal ratePercent;
    private long intervalTicks;

    // multipliers
    private static class MultiplierData {
        final BigDecimal multiplier;
        final long expiresAtMillis;

        MultiplierData(BigDecimal multiplier, long expiresAtMillis) {
            this.multiplier = multiplier;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    private final Map<UUID, MultiplierData> playerMultipliers = new ConcurrentHashMap<>();
    private volatile MultiplierData globalMultiplier;

    public InterestService(InvestmentsPlugin plugin, InvestmentManager investmentManager) {
        this.plugin = plugin;
        this.investmentManager = investmentManager;
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        double rate = plugin.getConfig().getDouble(ConfigKeys.INTEREST_RATE_PERCENT, 1.0D);
        int minutes = plugin.getConfig().getInt(ConfigKeys.INTEREST_INTERVAL_MINUTES, 10);

        this.ratePercent = BigDecimal.valueOf(rate);
        this.intervalTicks = Math.max(1L, minutes * 60L * 20L);
    }

    public void start() {
        if (ratePercent.compareTo(BigDecimal.ZERO) <= 0 || intervalTicks <= 0) {
            plugin.getLogger().warning("[Investments] Interest disabled (rate-percent <= 0 or invalid interval).");
            return;
        }

        plugin.getLogger().info("[Investments] Starting interest task: base rate=" + ratePercent + "% every " +
                (intervalTicks / 20L) + " seconds.");

        if (FoliaSchedulerUtil.isFolia()) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                    plugin,
                    scheduledTask -> tick(),
                    intervalTicks,
                    intervalTicks
            );
        } else {
            Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    this::tick,
                    intervalTicks,
                    intervalTicks
            );
        }
    }

    /** Set a player-specific multiplier for X minutes (e.g., 2.0 = 2x). */
    public void setPlayerMultiplier(UUID uuid, BigDecimal multiplier, int minutes) {
        long durationMillis = Math.max(0, minutes) * 60L * 1000L;
        long expiresAt = System.currentTimeMillis() + durationMillis;
        playerMultipliers.put(uuid, new MultiplierData(multiplier, expiresAt));
    }

    /** Set a global multiplier for X minutes for everyone. */
    public void setGlobalMultiplier(BigDecimal multiplier, int minutes) {
        long durationMillis = Math.max(0, minutes) * 60L * 1000L;
        long expiresAt = System.currentTimeMillis() + durationMillis;
        globalMultiplier = new MultiplierData(multiplier, expiresAt);
    }

    private BigDecimal getEffectiveMultiplier(UUID uuid) {
        long now = System.currentTimeMillis();
        BigDecimal result = BigDecimal.ONE;

        MultiplierData global = this.globalMultiplier;
        if (global != null) {
            if (now > global.expiresAtMillis) {
                globalMultiplier = null;
            } else if (global.multiplier.compareTo(BigDecimal.ZERO) > 0) {
                result = result.multiply(global.multiplier);
            }
        }

        MultiplierData per = playerMultipliers.get(uuid);
        if (per != null) {
            if (now > per.expiresAtMillis) {
                playerMultipliers.remove(uuid);
            } else if (per.multiplier.compareTo(BigDecimal.ZERO) > 0) {
                result = result.multiply(per.multiplier);
            }
        }

        return result;
    }

    /** Apply interest to all loaded profiles. */
    private void tick() {
        BigDecimal hundred = BigDecimal.valueOf(100);

        for (InvestmentProfile profile : investmentManager.getLoadedProfiles()) {
            boolean changed = false;

            BigDecimal multiplier = getEffectiveMultiplier(profile.getOwner());
            BigDecimal effectiveRate = ratePercent.multiply(multiplier);

            for (Investment inv : profile.getInvestments()) {
                if (inv.getInvested().compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal interest = inv.getInvested()
                        .multiply(effectiveRate)
                        .divide(hundred, 2, RoundingMode.DOWN);

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
