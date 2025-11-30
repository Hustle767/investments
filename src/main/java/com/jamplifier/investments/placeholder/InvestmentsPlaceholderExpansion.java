package com.jamplifier.investments.placeholder;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.investment.Investment;
import com.jamplifier.investments.investment.InvestmentManager;
import com.jamplifier.investments.investment.InvestmentProfile;
import com.jamplifier.investments.investment.InterestService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

public class InvestmentsPlaceholderExpansion extends PlaceholderExpansion {

    private final InvestmentsPlugin plugin;
    private final InvestmentManager investmentManager;
    private final InterestService interestService;

    public InvestmentsPlaceholderExpansion(InvestmentsPlugin plugin,
                                           InvestmentManager investmentManager,
                                           InterestService interestService) {
        this.plugin = plugin;
        this.investmentManager = investmentManager;
        this.interestService = interestService;
    }

    @Override
    public @NotNull String getIdentifier() {
        // %investments_<param>% ...
        return "investments";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        // keep this expansion registered across /papi reload
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        params = params.toLowerCase(Locale.ROOT);
        UUID uuid = player.getUniqueId();
        InvestmentProfile profile = investmentManager.getProfile(uuid);

        switch (params) {
            case "amount_invested": {
                BigDecimal total = BigDecimal.ZERO;
                for (Investment inv : profile.getInvestments()) {
                    total = total.add(inv.getInvested());
                }
                return total.toPlainString();
            }

            case "profit": {
                BigDecimal totalProfit = BigDecimal.ZERO;
                for (Investment inv : profile.getInvestments()) {
                    totalProfit = totalProfit.add(inv.getProfit());
                }
                return totalProfit.toPlainString();
            }

            case "interest_rate": {
                // display the base per-second % rate (or whatever your InterestService stores)
                BigDecimal rate = interestService.getRatePercent();
                return rate.toPlainString();
            }

            case "autocollect_status": {
                boolean enabled = profile.isAutoCollect();
                // color codes so menus/holograms show nicely
                return enabled ? "&aON" : "&cOFF";
            }

            default:
                return "";
        }
    }
}
