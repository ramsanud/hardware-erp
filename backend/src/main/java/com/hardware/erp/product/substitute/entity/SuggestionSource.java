package com.hardware.erp.product.substitute.entity;

/**
 * CR-089 §18. Which strategy produced a suggestion. A third value (AI) is
 * the extension point the brief asks the design to leave open - no AI
 * dependency exists today.
 */
public enum SuggestionSource {
    RULE_BASED(100),
    MANUAL_MAPPING(10);

    /**
     * Sort rank, lower first - the same numbers RecommendationStrategy
     * .priority() uses, so §17's "manual mappings outrank generic similarity"
     * is a property of the source, not of whichever strategy happened to
     * run first or of the score it awarded.
     */
    private final int rank;

    SuggestionSource(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
