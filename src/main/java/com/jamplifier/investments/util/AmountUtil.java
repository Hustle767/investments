package com.jamplifier.investments.util;

import java.math.BigDecimal;
import java.util.Locale;

public final class AmountUtil {

    private AmountUtil() {
    }

    /**
     * Parse an amount string that can be:
     *  - plain number: "10000"
     *  - with suffix: "10k", "1.5m", "2M", "3b", etc.
     *
     * Supported suffixes:
     *  k / K = thousand (1,000)
     *  m / M = million (1,000,000)
     *  b / B = billion (1,000,000,000)
     *
     * Returns null if invalid or <= 0.
     */
    public static BigDecimal parseAmount(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;

        BigDecimal multiplier = BigDecimal.ONE;

        char last = s.charAt(s.length() - 1);
        switch (last) {
            case 'k':
                multiplier = BigDecimal.valueOf(1_000L);
                s = s.substring(0, s.length() - 1);
                break;
            case 'm':
                multiplier = BigDecimal.valueOf(1_000_000L);
                s = s.substring(0, s.length() - 1);
                break;
            case 'b':
                multiplier = BigDecimal.valueOf(1_000_000_000L);
                s = s.substring(0, s.length() - 1);
                break;
            default:
                // no suffix
        }

        try {
            BigDecimal base = new BigDecimal(s.trim());
            BigDecimal result = base.multiply(multiplier);
            if (result.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return result;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
