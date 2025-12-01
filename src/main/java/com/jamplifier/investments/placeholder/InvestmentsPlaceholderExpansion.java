package com.jamplifier.investments.placeholder;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.investment.InvestmentManager;
import com.jamplifier.investments.investment.InvestmentProfile;
import com.jamplifier.investments.investment.InterestService;
import com.jamplifier.investments.util.AmountUtil;
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
            return AmountUtil.formatShort(total);
        }

        case "profit": {
            BigDecimal profit = profile.getTotalProfit();
            return AmountUtil.formatShort(profit);
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
                return AmountUtil.formatShort(perSecond) + "/s";
            }



            default:
                return null;
        }
    }
}
