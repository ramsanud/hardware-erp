package com.hardware.erp.product.substitute.strategy;

import com.hardware.erp.inventory.entity.Stock;
import com.hardware.erp.inventory.repository.StockRepository;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.product.substitute.config.SubstituteScoringProperties;
import com.hardware.erp.product.substitute.entity.SuggestionSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CR-089 §5/§6. Attribute similarity, scored out of
 * SubstituteScoringProperties.maximumScore() (110 by default). No external
 * service, no model, no API cost - the brief is explicit that the initial
 * implementation must work without any of those.
 *
 * Every weight comes from configuration; this class contains no scoring
 * literal of its own. The candidate pool is narrowed in SQL first
 * (category or product type, active, enough stock - §22), so a big
 * catalogue is never scored row by row in Java.
 *
 * Attribute comparison is null-safe and case/whitespace-insensitive in one
 * place (`same`): a shop that typed "Stainless Steel" on one product and
 * "stainless steel " on another means the same thing, and a missing
 * attribute on either side scores nothing rather than counting as a match
 * - "both blank" is not evidence of similarity.
 */
@Component
@RequiredArgsConstructor
public class RuleBasedRecommendationStrategy implements RecommendationStrategy {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final SubstituteScoringProperties weights;

    @Override
    public int priority() {
        return SuggestionSource.RULE_BASED.rank();
    }

    @Override
    public List<ScoredSuggestion> suggest(SubstituteContext context) {
        Product requested = context.requestedProduct();
        Long categoryId = requested.getCategory() == null ? null : requested.getCategory().getId();
        if (categoryId == null && isBlank(requested.getProductType())) {
            // Nothing to narrow by. Scoring the whole catalogue would be both
            // slow and meaningless - a product with no category and no type
            // has no evidence of similarity to anything (§22, §16).
            return List.of();
        }

        List<Product> candidates = productRepository.findSubstituteCandidates(
                context.tenantId(), requested.getId(), categoryId,
                blankToNull(requested.getProductType()), context.requestedQuantity());
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, BigDecimal> stockByProductId = stockRepository
                .findByTenantIdAndProductIdIn(context.tenantId(),
                        candidates.stream().map(Product::getId).toList())
                .stream()
                .collect(Collectors.toMap(stock -> stock.getProduct().getId(), Stock::getQuantityOnHand,
                        (first, second) -> first));

        List<ScoredSuggestion> scored = new ArrayList<>(candidates.size());
        for (Product candidate : candidates) {
            scored.add(score(requested, candidate,
                    stockByProductId.getOrDefault(candidate.getId(), BigDecimal.ZERO)));
        }
        return scored;
    }

    /** Package-private so the unit test can score a pair directly without a repository. */
    ScoredSuggestion score(Product requested, Product candidate, BigDecimal candidateStock) {
        int total = 0;
        List<String> matched = new ArrayList<>();
        List<String> differed = new ArrayList<>();

        total += award(sameCategory(requested, candidate), weights.sameCategory(), "category", matched, differed);
        total += award(same(requested.getProductType(), candidate.getProductType()),
                weights.sameProductType(), "product type", matched, differed);
        total += award(same(requested.getUsageType(), candidate.getUsageType()),
                weights.sameUsage(), "usage", matched, differed);
        total += award(same(requested.getSizeLabel(), candidate.getSizeLabel()),
                weights.sameSize(), "size", matched, differed);
        total += award(same(requested.getMaterial(), candidate.getMaterial()),
                weights.sameMaterial(), "material", matched, differed);
        total += award(sameBrand(requested, candidate), weights.sameBrand(), "brand", matched, differed);
        total += award(similarPrice(requested, candidate), weights.similarPrice(), "price", matched, differed);

        // Availability is a commercial factor, not a similarity one (§5's own
        // split): it cannot make an unlike product like, but between two
        // equally similar products the one actually on the shelf wins.
        if (candidateStock.compareTo(BigDecimal.ZERO) > 0) {
            total += weights.inStock();
        }

        return ScoredSuggestion.of(candidate, total, explain(matched, differed), SuggestionSource.RULE_BASED);
    }

    private int award(boolean matches, int weight, String label, List<String> matched, List<String> differed) {
        if (matches) {
            matched.add(label);
            return weight;
        }
        differed.add(label);
        return 0;
    }

    /**
     * §15. The reason is written for a shop owner deciding whether to offer
     * the product, not for a developer reading a score breakdown - so it
     * names what matched and what did not, in words, and says nothing it
     * cannot show evidence for.
     */
    private String explain(List<String> matched, List<String> differed) {
        StringBuilder reason = new StringBuilder();
        if (matched.isEmpty()) {
            reason.append("Nothing in common beyond being in the same part of your catalogue.");
        } else {
            reason.append("Same ").append(join(matched)).append('.');
        }
        if (!differed.isEmpty()) {
            reason.append(" Differs on ").append(join(differed)).append('.');
        }
        return reason.length() > 500 ? reason.substring(0, 500) : reason.toString();
    }

    private String join(List<String> parts) {
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    private boolean sameCategory(Product requested, Product candidate) {
        return requested.getCategory() != null && candidate.getCategory() != null
                && requested.getCategory().getId().equals(candidate.getCategory().getId());
    }

    private boolean sameBrand(Product requested, Product candidate) {
        return requested.getBrand() != null && candidate.getBrand() != null
                && requested.getBrand().getId().equals(candidate.getBrand().getId());
    }

    /**
     * Within the configured tolerance of the requested product's selling
     * price, in either direction. A zero-priced requested product (a service
     * line, or one nobody has priced yet) has no meaningful "similar" band,
     * so nothing scores here rather than everything scoring.
     */
    private boolean similarPrice(Product requested, Product candidate) {
        long requestedPrice = requested.getSellingPricePaise() == null ? 0L : requested.getSellingPricePaise();
        long candidatePrice = candidate.getSellingPricePaise() == null ? 0L : candidate.getSellingPricePaise();
        if (requestedPrice <= 0) {
            return false;
        }
        double difference = Math.abs(candidatePrice - requestedPrice) / (double) requestedPrice;
        return difference <= weights.similarPriceTolerance();
    }

    /** Null-safe, case-insensitive, whitespace-trimmed. Two blanks are NOT a match. */
    private boolean same(String left, String right) {
        if (isBlank(left) || isBlank(right)) {
            return false;
        }
        return left.trim().toLowerCase(Locale.ROOT).equals(right.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
