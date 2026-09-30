package com.hardware.erp.product.substitute.strategy;

import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.substitute.entity.MatchLevel;
import com.hardware.erp.product.substitute.entity.SuggestionSource;

/**
 * CR-089. One candidate a strategy put forward, with the score and the
 * plain-English reason §15 requires ("Same category, same size and same
 * usage. Stainless-steel material differs from the requested product.") -
 * transparency is the point: the owner must be able to disagree with the
 * engine, which means seeing why it said what it said.
 */
public record ScoredSuggestion(
        Product product,
        int score,
        MatchLevel matchLevel,
        String reason,
        SuggestionSource source
) {
    public static ScoredSuggestion of(Product product, int score, String reason, SuggestionSource source) {
        return new ScoredSuggestion(product, score, MatchLevel.of(score), reason, source);
    }
}
