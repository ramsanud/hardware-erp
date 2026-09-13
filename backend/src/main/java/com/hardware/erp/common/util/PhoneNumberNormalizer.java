package com.hardware.erp.common.util;

import com.hardware.erp.common.exception.BusinessException;

import java.util.regex.Pattern;

/**
 * The one place a stored phone number becomes an E.164 dialling string
 * (CR-080).
 *
 * Until CR-080 there were two near-copies of this - one in the Meta WhatsApp
 * provider, one in the Twilio SMS provider - that agreed on Indian numbers
 * and quietly differed on everything else. A number that reaches a customer
 * over SMS but not over WhatsApp, because two helpers disagreed about a
 * hyphen, is a bug nobody would ever trace; so there is now one helper and
 * the providers strip or keep the leading '+' as their API requires.
 *
 * <p>Rules, in the order they are tried:
 * <ol>
 *   <li>Spaces, hyphens, dots and brackets are formatting, not data - removed.</li>
 *   <li>A leading '+' means "already international": kept as given.</li>
 *   <li>A leading '00' is the same thing spelled the way a landline dials it.</li>
 *   <li>Bare digits are only ever read as <b>Indian</b>: ten digits starting
 *       6-9 (how every Customer/Supplier/User in this app stores a mobile),
 *       the same with a trunk '0' in front, or with '91' already in front.</li>
 *   <li>Anything else is refused. An eleven-digit number that is not Indian
 *       could be any of a dozen countries; guessing one would dial a stranger.</li>
 * </ol>
 */
public final class PhoneNumberNormalizer {

    /** E.164: '+', a non-zero country code, 8 to 15 digits in all. */
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final Pattern INDIAN_MOBILE = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern FORMATTING = Pattern.compile("[\\s\\-.()]");

    public static final String INVALID_MESSAGE = "Please add a valid customer WhatsApp number.";

    private PhoneNumberNormalizer() {
    }

    /**
     * Returns the number as {@code +<country><subscriber>}, or throws a
     * {@link BusinessException} the UI can show as-is. Never returns a
     * "best effort" value: a number this cannot vouch for is not a number
     * worth messaging.
     */
    public static String toE164(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(INVALID_MESSAGE);
        }
        String compact = FORMATTING.matcher(raw.trim()).replaceAll("");

        String candidate;
        if (compact.startsWith("+")) {
            candidate = compact;
        } else if (compact.startsWith("00")) {
            candidate = "+" + compact.substring(2);
        } else if (compact.chars().allMatch(Character::isDigit)) {
            candidate = indianToE164(compact);
        } else {
            candidate = compact;
        }

        if (candidate == null || !E164.matcher(candidate).matches()) {
            throw new BusinessException(INVALID_MESSAGE);
        }
        return candidate;
    }

    /** {@link #toE164} without the '+', which is how Meta's Cloud API and wa.me want it. */
    public static String toE164Digits(String raw) {
        return toE164(raw).substring(1);
    }

    /** True when {@link #toE164} would succeed - for callers that want to disable a button rather than catch. */
    public static boolean isValid(String raw) {
        try {
            toE164(raw);
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    private static String indianToE164(String digits) {
        if (INDIAN_MOBILE.matcher(digits).matches()) {
            return "+91" + digits;
        }
        // Trunk prefix, as people write it on a shop sign: 098765 43210.
        if (digits.length() == 11 && digits.startsWith("0")
                && INDIAN_MOBILE.matcher(digits.substring(1)).matches()) {
            return "+91" + digits.substring(1);
        }
        // Country code typed without the '+': 91 98765 43210.
        if (digits.length() == 12 && digits.startsWith("91")
                && INDIAN_MOBILE.matcher(digits.substring(2)).matches()) {
            return "+" + digits;
        }
        return null;
    }
}
