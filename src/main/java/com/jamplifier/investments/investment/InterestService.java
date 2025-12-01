package com.jamplifier.investments.investment;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.util.ConfigKeys;
import com.jamplifier.investments.util.FoliaSchedulerUtil;
import com.jamplifier.investments.util.MessageUtils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InterestService {

    private final InvestmentsPlugin plugin;
    private final InvestmentManager investmentManager;
    private final Economy economy; // <-- NEW

    private BigDecimal ratePercent;
    private long intervalTicks;

    // autocollect config cache
    private boolean autocollectEnabled;
    private String autocollectPermission;

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

    // scheduler handles
    private ScheduledTask foliaTask;
    private BukkitTask bukkitTask;

    // notification settings
    private boolean notificationsEnabled;
    private boolean notifyDefaultEnabled;
    private boolean notifyChatEnabled;
    private boolean notifyActionbarEnabled;
    private String notifyChatMessage;
    private String notifyActionbarMessage;

    // per-player toggle (override default)
    private final Map<UUID, Boolean> notifyOverrides = new ConcurrentHashMap<>();

    public InterestService(InvestmentsPlugin plugin, InvestmentManager investmentManager) {
        this.plugin = plugin;
        this.investmentManager = investmentManager;
        // pull Vault economy from your existing hook
        this.economy = plugin.getEconomyHook().getEconomy();
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        double rate = plugin.getConfig().getDouble(ConfigKeys.INTEREST_RATE_PERCENT, 1.0D);
        int minutes = plugin.getConfig().getInt(ConfigKeys.INTEREST_INTERVAL_MINUTES, 10);

        this.ratePercent = BigDecimal.valueOf(rate);
        this.intervalTicks = Math.max(1L, minutes * 60L * 20L);

        var cfg = plugin.getConfig();

        // notifications config
        notificationsEnabled = cfg.getBoolean("notifications.enabled", true);
        notifyDefaultEnabled = cfg.getBoolean("notifications.default-enabled", true);
        notifyChatEnabled = cfg.getBoolean("notifications.chat.enabled", true);
        notifyChatMessage = cfg.getString("notifications.chat.message",
                "&8[&aInvestments&8] &7You earned &a%amount% &7profit (&e%rate%%%&7).");
        notifyActionbarEnabled = cfg.getBoolean("notifications.actionbar.enabled", true);
        notifyActionbarMessage = cfg.getString("notifications.actionbar.message",
                "&a+%amount% &7investment profit (&e%rate%%%&7)");

        // autocollect config
        autocollectEnabled = cfg.getBoolean("autocollect.enabled", true);
        autocollectPermission = cfg.getString("autocollect.permission", "investments.autocollect");

        restart();
    }

    public void start() {
        restart();
    }

    private void restart() {
        if (foliaTask != null) {
            foliaTask.cancel();
            foliaTask = null;
        }
        if (bukkitTask != null) {
            bukkitTask.cancel();
            bukkitTask = null;
        }

        if (ratePercent.compareTo(BigDecimal.ZERO) <= 0 || intervalTicks <= 0) {
            plugin.getLogger().warning("[Investments] Interest disabled (rate-percent <= 0 or invalid interval).");
            return;
        }

        plugin.getLogger().info("[Investments] (Re)starting interest task: base rate=" + ratePercent +
                "% every " + (intervalTicks / 20L) + " seconds.");

        if (FoliaSchedulerUtil.isFolia()) {
            foliaTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                    plugin,
                    scheduledTask -> tick(),
                    intervalTicks,
                    intervalTicks
            );
        } else {
            bukkitTask = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    this::tick,
                    intervalTicks,
                    intervalTicks
            );
        }
    }

    // ---- Multipliers ----

    public void setPlayerMultiplier(UUID uuid, BigDecimal multiplier, int minutes) {
        long durationMillis = Math.max(0, minutes) * 60L * 1000L;
        long expiresAt = System.currentTimeMillis() + durationMillis;
        playerMultipliers.put(uuid, new MultiplierData(multiplier, expiresAt));
    }

    public void setGlobalMultiplier(BigDecimal multiplier, int minutes) {
        long durationMillis = Math.max(0, minutes) * 60L * 1000L;
        long expiresAt = System.currentTimeMillis() + durationMillis;
        globalMultiplier = new MultiplierData(multiplier, expiresAt);
    }

    // ---- Notification toggles ----

    public boolean toggleNotify(UUID uuid) {
        boolean current = isNotifyEnabled(uuid);
        boolean next = !current;
        notifyOverrides.put(uuid, next);
        return next;
    }

    public boolean isNotifyEnabled(UUID uuid) {
        Boolean override = notifyOverrides.get(uuid);
        if (override != null) {
            return override;
        }
        return notifyDefaultEnabled;
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

        // How many seconds does a single tick represent?
        long secondsThisTick = intervalTicks / 20L; // 20 ticks = 1 second
        BigDecimal secondsBD = BigDecimal.valueOf(secondsThisTick);

        for (InvestmentProfile profile : investmentManager.getLoadedProfiles()) {
            boolean changed = false;
            BigDecimal earnedThisTick = BigDecimal.ZERO;

            UUID owner = profile.getOwner();
            BigDecimal multiplier = getEffectiveMultiplier(owner); // player + global multipliers

            // ratePercent = per-second %, so:
            // effectivePercentForTick = ratePerSecond * seconds * multipliers
            BigDecimal effectiveRateForTick = ratePercent
                    .multiply(multiplier)
                    .multiply(secondsBD);

            for (Investment inv : profile.getInvestments()) {
                if (inv.getInvested().compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal interest = inv.getInvested()
                        .multiply(effectiveRateForTick)
                        .divide(hundred, 2, RoundingMode.DOWN);

                if (interest.compareTo(BigDecimal.ZERO) > 0) {
                    inv.addProfit(interest);
                    earnedThisTick = earnedThisTick.add(interest);
                    changed = true;
                }
            }

            if (!changed) {
                continue;
            }

            // --- AUTO-COLLECT LOGIC ---
            if (autocollectEnabled && profile.isAutoCollect()) {
                // Take ALL accumulated profit (including previous ticks)
                BigDecimal toCollect = profile.collectAllProfit();

                if (toCollect.compareTo(BigDecimal.ZERO) > 0 && economy != null) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(owner);
                    economy.depositPlayer(offline, toCollect.doubleValue());
                }

                // Save profile after clearing profit
                investmentManager.saveProfile(profile);
            } else {
                // No auto-collect: just save updated profit
                investmentManager.saveProfile(profile);
            }

            // Send the usual notification for this tick’s earnings
            sendNotification(owner, earnedThisTick, effectiveRateForTick);
        }
    }

    private void sendNotification(UUID uuid, BigDecimal amount, BigDecimal rate) {
        if (!notificationsEnabled) return;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;
        if (!isNotifyEnabled(uuid)) return;

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", amount.toPlainString());
        placeholders.put("rate", rate.toPlainString());

        FoliaSchedulerUtil.runForEntity(player, () -> {
            if (notifyChatEnabled) {
                String msg = notifyChatMessage;
                for (Map.Entry<String, String> e : placeholders.entrySet()) {
                    msg = msg.replace("%" + e.getKey() + "%", e.getValue());
                }
                player.sendMessage(MessageUtils.color(msg));
            }

            if (notifyActionbarEnabled) {
                String msg = notifyActionbarMessage;
                for (Map.Entry<String, String> e : placeholders.entrySet()) {
                    msg = msg.replace("%" + e.getKey() + "%", e.getValue());
                }
                player.sendActionBar(Component.text(MessageUtils.color(msg)));
            }
        });
    }

    // GETTERS
    public BigDecimal getRatePercent() {
        return ratePercent;
    }

    public BigDecimal getBaseRatePercentPerSecond() {
        return ratePercent;
    }
}
