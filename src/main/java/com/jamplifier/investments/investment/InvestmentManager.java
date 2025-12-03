package com.jamplifier.investments.investment;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.storage.InvestmentStorage;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InvestmentManager {

    private final InvestmentsPlugin plugin;
    private final InvestmentStorage storage;
    private final MaxInvestPermissionService maxInvestPermissionService;

    private final Map<UUID, InvestmentProfile> cache = new ConcurrentHashMap<>();

    public InvestmentManager(InvestmentsPlugin plugin, InvestmentStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.maxInvestPermissionService = new MaxInvestPermissionService(plugin);
    }

    public InvestmentProfile getProfile(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> {
            InvestmentProfile profile = new InvestmentProfile(id);
            profile.load(storage);
            return profile;
        });
    }

    public void saveProfile(InvestmentProfile profile) {
        profile.save(storage);
    }

    public int getMaxInvestments(Player player) {
        return maxInvestPermissionService.getMaxInvestments(player);
    }

    /**
     * Returns the max total-invested amount allowed for this player.
     * - If they have a permission "invest.maxlimit.<amount>", the highest such
     *   amount is used.
     * - Otherwise the config value "default-max-invest-amount" is used.
     * - If both are <= 0, this means "no cap" and BigDecimal.ZERO is returned.
     */
    public BigDecimal getMaxTotalAmount(Player player) {
        // default from config: 0 or negative means "no cap"
        double defaultMaxDouble = plugin.getConfig().getDouble("default-max-invest-amount", 0.0D);
        BigDecimal defaultMax = defaultMaxDouble > 0
                ? BigDecimal.valueOf(defaultMaxDouble)
                : BigDecimal.ZERO;

        BigDecimal permMax = maxInvestPermissionService.getMaxTotalAmount(player);
        if (permMax != null && permMax.compareTo(BigDecimal.ZERO) > 0) {
            return permMax;
        }

        return defaultMax;
    }

    public void reloadPermissions() {
        maxInvestPermissionService.reload();
    }

    public boolean addInvestment(Player player, BigDecimal amount) {
        InvestmentProfile profile = getProfile(player.getUniqueId());
        int max = getMaxInvestments(player);

        if (max > 0 && profile.getInvestments().size() >= max) {
            return false;
        }

        profile.addInvestment(amount);
        saveProfile(profile);
        return true;
    }

    public BigDecimal collectProfit(Player player) {
        InvestmentProfile profile = getProfile(player.getUniqueId());
        BigDecimal collected = profile.collectAllProfit();
        saveProfile(profile);
        return collected;
    }

    public void deleteInvestments(Player player) {
        InvestmentProfile profile = getProfile(player.getUniqueId());
        profile.deleteAllInvestments();
        saveProfile(profile);
    }

    /** All currently loaded profiles (players that have interacted with the plugin). */
    public Iterable<InvestmentProfile> getLoadedProfiles() {
        return cache.values();
    }
}
