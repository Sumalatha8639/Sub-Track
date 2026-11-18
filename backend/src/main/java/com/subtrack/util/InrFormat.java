package com.subtrack.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formats rupees with Indian digit grouping: 123456.5 -> "₹1,23,456.50".
 * Java's NumberFormat can't do this (it only supports a fixed group size), so it is done by hand.
 */
public final class InrFormat {

    private InrFormat() {
    }

    public static String format(BigDecimal amount) {
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        String sign = value.signum() < 0 ? "-" : "";
        String plain = value.abs().toPlainString();
        int dot = plain.indexOf('.');
        String whole = plain.substring(0, dot);
        String fraction = plain.substring(dot);
        return sign + "₹" + groupIndian(whole) + fraction;
    }

    /** Last three digits, then groups of two: 12345678 -> 1,23,45,678. */
    static String groupIndian(String digits) {
        if (digits.length() <= 3) {
            return digits;
        }
        String last3 = digits.substring(digits.length() - 3);
        String rest = digits.substring(0, digits.length() - 3);
        StringBuilder out = new StringBuilder();
        int first = rest.length() % 2;
        if (first == 1) {
            out.append(rest, 0, 1);
        }
        for (int i = first; i < rest.length(); i += 2) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(rest, i, i + 2);
        }
        return out.append(',').append(last3).toString();
    }
}
