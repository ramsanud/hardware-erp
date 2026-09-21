package com.hardware.erp.product.substitute.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CR-089 §6. The scoring weights, in ONE place - the brief's own
 * instruction is that they "must be configurable and should NOT be
 * hardcoded throughout the application". Nothing outside
 * RuleBasedRecommendationStrategy reads them, and that class holds no
 * literal of its own.
 *
 * Defaults are the brief's worked example, summing to 110:
 * category 30, product type 20, usage 20, size 15, material 5, brand 5,
 * similar price 5, in stock 10.
 *
 * Deliberately platform config rather than a per-tenant row: an owner
 * tuning "same material is worth 5 or 7" is a support call waiting to
 * happen. What an owner genuinely decides - the minimum score to show and
 * whether to show over-budget products - is per-tenant, in
 * `substitute_setting`.
 */
@ConfigurationProperties(prefix = "app.substitute.scoring")
public record SubstituteScoringProperties(
        Integer sameCategory,
        Integer sameProductType,
        Integer sameUsage,
        Integer sameSize,
        Integer sameMaterial,
        Integer sameBrand,
        Integer similarPrice,
        Integer inStock,
        /** How far from the requested product's price still counts as "similar", as a fraction: 0.20 = within 20%. */
        Double similarPriceTolerance
) {
    public SubstituteScoringProperties {
        sameCategory = orDefault(sameCategory, 30);
        sameProductType = orDefault(sameProductType, 20);
        sameUsage = orDefault(sameUsage, 20);
        sameSize = orDefault(sameSize, 15);
        sameMaterial = orDefault(sameMaterial, 5);
        sameBrand = orDefault(sameBrand, 5);
        similarPrice = orDefault(similarPrice, 5);
        inStock = orDefault(inStock, 10);
        if (similarPriceTolerance == null || similarPriceTolerance <= 0) {
            similarPriceTolerance = 0.20;
        }
    }

    private static Integer orDefault(Integer value, int fallback) {
        return value == null || value < 0 ? fallback : value;
    }

    /** The highest score the rule-based strategy can award - the denominator the match levels in §7 assume. */
    public int maximumScore() {
        return sameCategory + sameProductType + sameUsage + sameSize
                + sameMaterial + sameBrand + similarPrice + inStock;
    }
}
