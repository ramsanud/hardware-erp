package com.hardware.erp.inventory.repository;

import com.hardware.erp.inventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("select m from StockMovement m "
         + "where m.tenant.id = :tenantId and m.product.id = :productId "
         + "order by m.createdAt desc, m.id desc")
    Page<StockMovement> findByProduct(@Param("tenantId") Long tenantId,
                                      @Param("productId") Long productId,
                                      Pageable pageable);
}
