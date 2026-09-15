package com.hardware.erp.common.util;

import java.util.regex.Pattern;

/**
 * CR-087. GSTIN structure and checksum.
 *
 * A GSTIN is 15 characters: 2-digit state code, 10-character PAN, an entity
 * number, the letter Z, and a check character. The check character is a
 * Modulo-36 checksum over the first 14 (the GSTN's published scheme, the
 * same one the GST portal applies): each character maps to 0-35, is
 * multiplied by 1 or 2 alternately, the quotient and remainder by 36 of that
 * product are added, and the check is {@code (36 - sum mod 36) mod 36}.
 *
 * A regex alone accepts one wrong keystroke in fourteen positions; the
 * checksum rejects almost all of them, which is the difference between a
 * typo caught at the counter and a GSTR-1 rejected at the portal.
 */
public final class Gstin {

    /** The structural pattern every layer already used; the checksum is applied on top of it. */
    public static final Pattern STRUCTURE = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]$");

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private Gstin() {
    }

    /** True for null/blank (absence is allowed everywhere a GSTIN is optional) and for a well-formed, checksum-valid GSTIN. */
    public static boolean isValidOrBlank(String gstin) {
        return gstin == null || gstin.isBlank() || isValid(gstin);
    }

    public static boolean isValid(String gstin) {
        if (gstin == null || !STRUCTURE.matcher(gstin).matches()) {
            return false;
        }
        return gstin.charAt(14) == checkCharacter(gstin.substring(0, 14));
    }

    /** The check character for a 14-character GSTIN prefix. Exposed so tests and seeds can build valid numbers. */
    public static char checkCharacter(String first14) {
        if (first14 == null || first14.length() != 14) {
            throw new IllegalArgumentException("A GSTIN prefix has exactly 14 characters");
        }
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int code = ALPHABET.indexOf(first14.charAt(i));
            if (code < 0) {
                throw new IllegalArgumentException("GSTIN characters are digits and capital letters only");
            }
            int product = code * (i % 2 == 0 ? 1 : 2);
            sum += product / 36 + product % 36;
        }
        return ALPHABET.charAt((36 - sum % 36) % 36);
    }
}
