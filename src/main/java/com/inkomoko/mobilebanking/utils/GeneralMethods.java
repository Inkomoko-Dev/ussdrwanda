package com.inkomoko.mobilebanking.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class GeneralMethods {

    private GeneralMethods() {
    }

    public static int parseInputAsInteger(String input) {
        try {
            return Integer.parseInt(input == null ? "" : input.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Parses a whole-number RWF amount; returns null when not a positive integer. */
    public static BigDecimal parseAmount(String input) {
        if (input == null) return null;
        String s = input.trim().replace(",", "");
        if (!s.matches("[0-9]{1,9}")) return null;
        BigDecimal v = new BigDecimal(s);
        return v.signum() > 0 ? v : null;
    }

    /** 250000 → "250,000" (RWF has no minor unit on screen). */
    public static String formatAmount(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        DecimalFormat df = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        return df.format(amount.setScale(0, RoundingMode.HALF_UP));
    }

    public static String rwf(BigDecimal amount) {
        return "RWF " + formatAmount(amount);
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
