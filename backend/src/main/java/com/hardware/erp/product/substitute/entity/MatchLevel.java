package com.hardware.erp.product.substitute.entity;

/**
 * CR-089 §7. Bands over the 0-110 score. DO_NOT_RECOMMEND exists as a
 * value so a scored-but-rejected candidate can still be explained if
 * anyone asks why it was left out; it is never shown as a suggestion.
 */
public enum MatchLevel {
    EXCELLENT, HIGH, MEDIUM, LOW, DO_NOT_RECOMMEND;

    public static MatchLevel of(int score) {
        if (score >= 90) {
            return EXCELLENT;
        }
        if (score >= 75) {
            return HIGH;
        }
        if (score >= 60) {
            return MEDIUM;
        }
        if (score >= 40) {
            return LOW;
        }
        return DO_NOT_RECOMMEND;
    }
}
