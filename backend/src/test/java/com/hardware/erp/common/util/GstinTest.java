package com.hardware.erp.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** CR-087. */
class GstinTest {

    /** The first two are publicly listed GSTINs (Uber India, Reliance Retail) - real check characters, not invented ones. */
    @ParameterizedTest
    @ValueSource(strings = {"27AAPFU0939F1ZV", "27AAACR5055K1Z7", "33AABCS1429B1Z1", "29AABCS1429B1ZQ"})
    @DisplayName("a GSTIN whose check character matches the Modulo-36 sum is valid")
    void acceptsChecksumValid(String gstin) {
        assertThat(Gstin.isValid(gstin)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "27AAPFU0939F1ZW",   // one wrong check character
            "27AAPFU0939F1ZV ",  // trailing space
            "27AAPFU0938F1ZV",   // one wrong digit in the PAN
            "33AABCS1429B1ZP",   // the old example value: structurally fine, checksum wrong
            "NOTAGSTIN", "", "27AAPFU0939F1Z"})
    @DisplayName("a single wrong character anywhere in the first fourteen fails the checksum")
    void rejectsChecksumInvalid(String gstin) {
        assertThat(Gstin.isValid(gstin)).isFalse();
    }

    @Test
    @DisplayName("absence is allowed - every GSTIN field in the app is optional")
    void blankIsAllowedWhereOptional() {
        assertThat(Gstin.isValidOrBlank(null)).isTrue();
        assertThat(Gstin.isValidOrBlank("")).isTrue();
        assertThat(Gstin.isValidOrBlank("  ")).isTrue();
        assertThat(Gstin.isValidOrBlank("27AAPFU0939F1ZW")).isFalse();
    }

    @Test
    void checkCharacterReproducesTheRealOnes() {
        assertThat(Gstin.checkCharacter("27AAPFU0939F1Z")).isEqualTo('V');
        assertThat(Gstin.checkCharacter("27AAACR5055K1Z")).isEqualTo('7');
    }
}
