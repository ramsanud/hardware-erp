package com.hardware.erp.product.substitute.strategy;

import com.hardware.erp.inventory.repository.StockRepository;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.substitute.entity.ProductRelationship;
import com.hardware.erp.product.substitute.entity.SuggestionSource;
import com.hardware.erp.product.substitute.repository.ProductRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * CR-089 §17. The owner's own mappings, which outrank everything the
 * similarity scorer produces (priority 10 vs 100).
 *
 * §16 is the reason this exists at all: for electrical goods, plumbing
 * fittings, load-bearing hardware and locks, "the names look similar" is
 * not a safe basis for offering a substitute. An explicit mapping is a
 * human saying "these two really are interchangeable", so it is scored at
 * the top of the band rather than computed.
 *
 * COMPATIBLE relationships are excluded (see RelationshipType.isSubstitute):
 * a compatible product goes WITH the requested one, it does not replace it.
 */
@Component
@RequiredArgsConstructor
public class ManualMappingRecommendationStrategy implements RecommendationStrategy {

    /** Deliberately not 110: a mapping says "interchangeable", not "identical", and leaving headroom keeps EXCELLENT meaningful. */
    private static final int MANUAL_MAPPING_SCORE = 100;

    private final ProductRelationshipRepository relationshipRepository;
    private final StockRepository stockRepository;

    @Override
    public int priority() {
        return SuggestionSource.MANUAL_MAPPING.rank();
    }

    @Override
    public List<ScoredSuggestion> suggest(SubstituteContext context) {
        List<ProductRelationship> mappings = relationshipRepository.findForProduct(
                context.tenantId(), context.requestedProduct().getId());

        List<ScoredSuggestion> suggestions = new ArrayList<>();
        for (ProductRelationship mapping : mappings) {
            if (!mapping.getRelationshipType().isSubstitute()) {
                continue;
            }
            Product candidate = mapping.getRelatedProduct();
            if (candidate.getStatus() != ProductStatus.ACTIVE) {
                continue;
            }
            if (!hasEnoughStock(context, candidate)) {
                continue;
            }
            suggestions.add(ScoredSuggestion.of(candidate, MANUAL_MAPPING_SCORE,
                    reasonFor(mapping), SuggestionSource.MANUAL_MAPPING));
        }
        return suggestions;
    }

    private boolean hasEnoughStock(SubstituteContext context, Product candidate) {
        BigDecimal onHand = stockRepository.findByTenantIdAndProductId(context.tenantId(), candidate.getId())
                .map(stock -> stock.getQuantityOnHand())
                .orElse(BigDecimal.ZERO);
        return onHand.compareTo(context.requestedQuantity()) >= 0;
    }

    private String reasonFor(ProductRelationship mapping) {
        String base = switch (mapping.getRelationshipType()) {
            case ALTERNATIVE -> "Marked by your shop as an alternative to the requested product.";
            case UPGRADE -> "Marked by your shop as an upgrade on the requested product.";
            case LOWER_COST -> "Marked by your shop as a lower-cost option for the same job.";
            case SAME_USE -> "Marked by your shop as serving the same use.";
            case REPLACEMENT -> "Marked by your shop as the replacement for the requested product.";
            // Unreachable - filtered above - but a switch over an enum must be total.
            case COMPATIBLE -> "Marked by your shop as compatible.";
        };
        return mapping.getNotes() == null || mapping.getNotes().isBlank()
                ? base
                : base + " " + mapping.getNotes().trim();
    }
}
