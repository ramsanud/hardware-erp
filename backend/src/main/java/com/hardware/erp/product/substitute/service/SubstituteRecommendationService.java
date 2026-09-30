package com.hardware.erp.product.substitute.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.ComparisonResponse;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.CreateProductRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.CreateRelationshipRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.ProductRequestResponse;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.RelationshipResponse;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SelectAlternativeRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SubstituteSettingRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SubstituteSettingResponse;
import com.hardware.erp.product.substitute.entity.ProductRequestStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * CR-089 §19. The service every controller talks to; the strategies sit
 * behind it (§18's SubstituteRecommendationService -> RecommendationStrategy
 * shape) so adding an AI strategy later changes nothing above this line.
 *
 * Gated on FeatureKey.SMART_SUBSTITUTE (PREMIUM) inside the implementation,
 * not only in the controller - a future internal caller gets the same
 * answer as an HTTP one.
 */
public interface SubstituteRecommendationService {

    /** Records what the customer asked for and computes the alternatives in one step. */
    ProductRequestResponse createRequest(CreateProductRequest request);

    ProductRequestResponse get(Long requestId);

    PageResponse<ProductRequestResponse> search(ProductRequestStatus status, Pageable pageable);

    /** Recomputes and re-persists the alternatives for an existing request - stock and prices move. */
    ProductRequestResponse recomputeAlternatives(Long requestId);

    /** §13. The owner chooses; nothing is auto-substituted and no invoice is touched. */
    ProductRequestResponse selectAlternative(Long requestId, SelectAlternativeRequest request);

    ProductRequestResponse cancel(Long requestId);

    /** §12. Requested vs one alternative, attribute by attribute. */
    ComparisonResponse compare(Long requestId, Long alternativeProductId);

    List<RelationshipResponse> relationshipsFor(Long productId);

    RelationshipResponse createRelationship(Long productId, CreateRelationshipRequest request);

    void deleteRelationship(Long productId, Long relationshipId);

    SubstituteSettingResponse settings();

    SubstituteSettingResponse updateSettings(SubstituteSettingRequest request);
}
