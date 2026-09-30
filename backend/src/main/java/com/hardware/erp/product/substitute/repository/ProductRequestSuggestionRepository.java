package com.hardware.erp.product.substitute.repository;

import com.hardware.erp.product.substitute.entity.ProductRequestSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRequestSuggestionRepository extends JpaRepository<ProductRequestSuggestion, Long> {

    @Query("""
            SELECT s FROM ProductRequestSuggestion s
            JOIN FETCH s.suggestedProduct
            WHERE s.productRequest.id = :requestId
            ORDER BY s.score DESC, s.id
            """)
    List<ProductRequestSuggestion> findForRequest(Long requestId);

    /** Recomputing a request's alternatives replaces the previous set rather than appending to it. */
    @Modifying
    @Query("DELETE FROM ProductRequestSuggestion s WHERE s.productRequest.id = :requestId")
    void deleteForRequest(Long requestId);
}
