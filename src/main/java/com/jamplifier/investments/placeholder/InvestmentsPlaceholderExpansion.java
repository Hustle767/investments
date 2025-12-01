package com.jamplifier.investments.placeholder;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.investment.InvestmentManager;
import com.jamplifier.investments.investment.InvestmentProfile;
import com.jamplifier.investments.investment.InterestService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

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
        return "investments";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Jamplifier";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        // so it doesn't unregister on /papi reload
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        InvestmentProfile profile = investmentManager.getProfile(player.getUniqueId());
        String key = params.toLowerCase(Locale.ROOT);

        switch (key) {
            case "amount_invested": {
                BigDecimal total = profile.getTotalInvested();
                return total.toPlainString();
            }

            case "profit": {
                BigDecimal profit = profile.getTotalProfit();
                return profit.toPlainString();
            }

            case "interest_rate": {
                // base per-second % from config
                BigDecimal ratePerSecond = interestService.getBaseRatePercentPerSecond();
                return ratePerSecond.toPlainString();
            }

            case "autocollect_status": {
                return profile.isAutoCollect() ? "Enabled" : "Disabled";
            }

            case "earn_per_second": {
                BigDecimal total = profile.getTotalInvested();
                if (total.compareTo(BigDecimal.ZERO) <= 0) {
                    return "0";
                }

                BigDecimal ratePerSecond = interestService.getBaseRatePercentPerSecond();
                // earnings per second = invested * (rate% / 100)
                BigDecimal perSecond = total
                        .multiply(ratePerSecond)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);

                return perSecond.toPlainString();
            }

            case "earn_per_second_formatted": {
                BigDecimal total = profile.getTotalInvested();
                if (total.compareTo(BigDecimal.ZERO) <= 0) {
                    return "0/s";
                }

                BigDecimal ratePerSecond = interestService.getBaseRatePercentPerSecond();
                BigDecimal perSecond = total
                        .multiply(ratePerSecond)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);

                // formatted + "/s"
                return formatShort(perSecond) + "/s";
            }


            default:
                return null;
        }
    }

    /**
     * Formats numbers like:
     *  - 999      -> "999"
     *  - 1_234    -> "1.23k"
     *  - 1_200_000 -> "1.2M"
     *  - 3_450_000_000 -> "3.45B"
     */
    private String formatShort(BigDecimal value) {
        if (value == null) return "0";

        boolean negative = value.signum() < 0;
        BigDecimal abs = value.abs();

        BigDecimal thousand = new BigDecimal("1000");
        BigDecimal million  = new BigDecimal("1000000");
        BigDecimal billion  = new BigDecimal("1000000000");
        BigDecimal trillion = new BigDecimal("1000000000000");

        String suffix = "";
        BigDecimal divisor = BigDecimal.ONE;

        if (abs.compareTo(trillion) >= 0) {
            suffix = "T";
            divisor = trillion;
        } else if (abs.compareTo(billion) >= 0) {
            suffix = "B";
            divisor = billion;
        } else if (abs.compareTo(million) >= 0) {
            suffix = "M";
            divisor = million;
        } else if (abs.compareTo(thousand) >= 0) {
            suffix = "k";
            divisor = thousand;
        } else {
            // < 1000 → just show up to 2 decimals
            BigDecimal scaled = abs.setScale(2, RoundingMode.DOWN).stripTrailingZeros();
            return (negative ? "-" : "") + scaled.toPlainString();
        }

        BigDecimal shortVal = abs
                .divide(divisor, 2, RoundingMode.DOWN)
                .stripTrailingZeros();

        return (negative ? "-" : "") + shortVal.toPlainString() + suffix;
    }
}
