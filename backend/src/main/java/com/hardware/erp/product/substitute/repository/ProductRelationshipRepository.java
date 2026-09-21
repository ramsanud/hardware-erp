package com.hardware.erp.product.substitute.repository;

import com.hardware.erp.product.substitute.entity.ProductRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRelationshipRepository extends JpaRepository<ProductRelationship, Long> {

    /** JOIN FETCH the related product: the caller always renders its name, price and stock. */
    @Query("""
            SELECT r FROM ProductRelationship r
            JOIN FETCH r.relatedProduct
            WHERE r.tenant.id = :tenantId AND r.product.id = :productId
            ORDER BY r.relationshipType, r.id
            """)
    List<ProductRelationship> findForProduct(Long tenantId, Long productId);

    Optional<ProductRelationship> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndProductIdAndRelatedProductIdAndRelationshipType(
            Long tenantId, Long productId, Long relatedProductId,
            com.hardware.erp.product.substitute.entity.RelationshipType relationshipType);
}
