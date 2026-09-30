package com.hardware.erp.product.substitute.repository;

import com.hardware.erp.product.substitute.entity.ProductRequestRecord;
import com.hardware.erp.product.substitute.entity.ProductRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductRequestRepository extends JpaRepository<ProductRequestRecord, Long> {

    Optional<ProductRequestRecord> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
            SELECT r FROM ProductRequestRecord r
            JOIN FETCH r.requestedProduct
            WHERE r.tenant.id = :tenantId
              AND (:status IS NULL OR r.status = :status)
            """)
    Page<ProductRequestRecord> search(Long tenantId, ProductRequestStatus status, Pageable pageable);
}
