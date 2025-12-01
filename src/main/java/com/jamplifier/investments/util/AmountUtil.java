package com.jamplifier.investments.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /**
     * Formats numbers like:
     *  999                -> "999"
     *  1_234              -> "1.23k"
     *  1_200_000          -> "1.2M"
     *  3_450_000_000      -> "3.45B"
     *  7_890_000_000_000  -> "7.89T"
     */
    public static String formatShort(BigDecimal value) {
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

    public static String formatShort(double value) {
        return formatShort(BigDecimal.valueOf(value));
    }

    public static String formatShort(long value) {
        return formatShort(BigDecimal.valueOf(value));
    }
}
