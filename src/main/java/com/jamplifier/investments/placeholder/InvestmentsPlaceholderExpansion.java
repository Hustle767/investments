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
import java.math.RoundingMode;
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
    public String getIdentifier() {
        return "investments";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";

        InvestmentProfile profile = investmentManager.getProfile(player.getUniqueId());
        if (profile == null) return "";

        switch (params.toLowerCase(Locale.ROOT)) {

            case "amount_invested":
                return formatMoney(totalInvested(profile));

            case "profit":
                return formatMoney(totalProfit(profile));

            case "interest_rate":
                // base per-second percent
                BigDecimal rate = interestService.getBaseRatePercentPerSecond();
                return rate.stripTrailingZeros().toPlainString() + "%/s";

            case "autocollect_status":
                return profile.isAutoCollect() ? "ON" : "OFF";

            // NEW: $ earned per second, e.g. "$0.10/s"
            case "earn_per_second":
                return formatPerSecond(profile);

            default:
                return null;
        }
    }

    private BigDecimal totalInvested(InvestmentProfile profile) {
        BigDecimal total = BigDecimal.ZERO;
        for (Investment inv : profile.getInvestments()) {
            total = total.add(inv.getInvested());
        }
        return total;
    }

    private BigDecimal totalProfit(InvestmentProfile profile) {
        BigDecimal total = BigDecimal.ZERO;
        for (Investment inv : profile.getInvestments()) {
            total = total.add(inv.getProfit());
        }
        return total;
    }

    private String formatPerSecond(InvestmentProfile profile) {
        BigDecimal invested = totalInvested(profile);
        if (invested.compareTo(BigDecimal.ZERO) <= 0) {
            return "$0.00/s";
        }

        BigDecimal ratePercent = interestService.getBaseRatePercentPerSecond();
        BigDecimal perSecond = invested
                .multiply(ratePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);

        return "$" + perSecond.toPlainString() + "/s";
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) return "$0.00";
        return "$" + value.setScale(2, RoundingMode.DOWN).toPlainString();
    }
}