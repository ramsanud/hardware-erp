package com.hardware.erp.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * java.text.DecimalFormat has no notion of a repeating secondary grouping
 * size - a pattern like "##,##,##,##0.00" silently collapses to plain
 * 3-digit Western grouping (verified on JDK 21: format(500000) returns
 * "500,000.00", not "5,00,000.00"). Indian lakh/crore grouping - 3 digits
 * nearest the decimal point, then repeating groups of 2 - has to be built
 * by hand.
 */
public final class IndianCurrencyFormat {

    private IndianCurrencyFormat() {
    }

    /** Paise to a displayable rupee string, e.g. 50_000_000L -> "5,00,000.00". */
    public static String rupees(Long paise) {
        if (paise == null) {
            return "0.00";
        }
        BigDecimal value = BigDecimal.valueOf(paise)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return format(value);
    }

    private static String format(BigDecimal value) {
        boolean negative = value.signum() < 0;
        String plain = value.abs().setScale(2, RoundingMode.HALF_UP).toPlainString();
        int dot = plain.indexOf('.');
        String intPart = plain.substring(0, dot);
        String fracPart = plain.substring(dot + 1);
        return (negative ? "-" : "") + groupIndian(intPart) + "." + fracPart;
    }

    private static String groupIndian(String digits) {
        if (digits.length() <= 3) {
            return digits;
        }
        String lastThree = digits.substring(digits.length() - 3);
        String remainder = digits.substring(0, digits.length() - 3);
        StringBuilder grouped = new StringBuilder();
        int len = remainder.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 2 == 0) {
                grouped.append(',');
            }
            grouped.append(remainder.charAt(i));
        }
        return grouped + "," + lastThree;
    }
}
