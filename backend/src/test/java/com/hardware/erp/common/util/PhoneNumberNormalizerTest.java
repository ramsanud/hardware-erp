package com.hardware.erp.common.util;

import com.hardware.erp.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CR-080 - the single phone normaliser both the wa.me link and the Twilio /
 * Meta providers now share. Every accepted shape and every refused one is
 * pinned here, because "silently dial the wrong number" is the failure this
 * class exists to make impossible.
 */
class PhoneNumberNormalizerTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("every way a shop writes an Indian mobile becomes +91 and ten digits")
    @CsvSource({
            "9876543210,        +919876543210",   // bare, as every Customer/Supplier/User stores it
            "+919876543210,     +919876543210",   // already E.164
            "+91 98765 43210,   +919876543210",   // spaces
            "+91-98765-43210,   +919876543210",   // hyphens
            "91 9876543210,     +919876543210",   // country code, no plus
            "919876543210,      +919876543210",
            "09876543210,       +919876543210",   // trunk zero, as on a shop board
            "(+91) 98765.43210, +919876543210",   // brackets and dots
            "  9876543210  ,    +919876543210",   // surrounding whitespace
    })
    void indianNumbers(String input, String expected) {
        assertThat(PhoneNumberNormalizer.toE164(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("an international number is kept as its own country, never re-read as Indian")
    @CsvSource({
            "+14155550123,      +14155550123",
            "+44 20 7946 0958,  +442079460958",
            "0044 20 7946 0958, +442079460958",   // landline-style international prefix
            "+971 50 123 4567,  +971501234567",
    })
    void internationalNumbers(String input, String expected) {
        assertThat(PhoneNumberNormalizer.toE164(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" is refused")
    @DisplayName("anything it cannot vouch for is refused with the message the UI shows, never guessed at")
    @ValueSource(strings = {
            "",
            "   ",
            "12345",              // too short to be anyone
            "5876543210",         // ten digits, but Indian mobiles start 6-9
            "98765432101",        // eleven bare digits that are not trunk-zero: could be many countries
            "4155550123",         // a US number typed without +1: ten digits, but not an Indian mobile
            "+0123456789",        // country codes never start with 0
            "+1",                 // '+' and one digit
            "+12345678901234567", // longer than E.164 allows
            "abc",
            "98765abc43",
            "+91 98765 4321O",    // letter O for zero - a real typo, refused rather than mangled
    })
    void refused(String input) {
        assertThatThrownBy(() -> PhoneNumberNormalizer.toE164(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PhoneNumberNormalizer.INVALID_MESSAGE);
    }

    @Test
    @DisplayName("null is refused the same way as blank")
    void nullIsRefused() {
        assertThatThrownBy(() -> PhoneNumberNormalizer.toE164(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("the digits-only form drops exactly the leading '+', which is what wa.me and Meta want")
    void digitsOnlyForm() {
        assertThat(PhoneNumberNormalizer.toE164Digits("98765 43210")).isEqualTo("919876543210");
        assertThat(PhoneNumberNormalizer.toE164Digits("+44 20 7946 0958")).isEqualTo("442079460958");
    }

    @Test
    @DisplayName("isValid answers without throwing, so a button can be disabled instead of a request failing")
    void isValid() {
        assertThat(PhoneNumberNormalizer.isValid("9876543210")).isTrue();
        assertThat(PhoneNumberNormalizer.isValid("12345")).isFalse();
        assertThat(PhoneNumberNormalizer.isValid(null)).isFalse();
    }
}
