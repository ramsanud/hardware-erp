package com.hardware.erp.product.substitute.strategy;

import java.util.List;

/**
 * CR-089 §18. The extension point an AI strategy would plug into later
 * without touching SubstituteRecommendationService or anything above it.
 * Two implementations exist today and neither calls an external API:
 * ManualMappingRecommendationStrategy (the owner's own mappings) and
 * RuleBasedRecommendationStrategy (attribute scoring).
 *
 * Strategies are asked in priority() order, lowest first, and a product
 * already suggested by an earlier strategy is never re-suggested by a
 * later one - which is exactly how §17's "manual mappings outrank generic
 * similarity" is enforced, rather than by a special case in the service.
 */
public interface RecommendationStrategy {

    /** Lower runs first and wins ties. Manual mapping is 10, rule-based 100. */
    int priority();

    /** Candidates this strategy puts forward, unfiltered by score threshold or result limit - the service applies both. */
    List<ScoredSuggestion> suggest(SubstituteContext context);
}
