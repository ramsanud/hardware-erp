package com.hardware.erp.inventory.repository;

import com.hardware.erp.inventory.entity.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByTenantIdAndProductId(Long tenantId, Long productId);

    /**
     * Locked read-modify-write: two simultaneous sales of the same product
     * must not both read the same starting quantity and silently overwrite
     * each other's decrement (the same class of race CR-016's
     * lockActiveOwners guards against).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.tenant.id = :tenantId and s.product.id = :productId")
    Optional<Stock> lockByTenantIdAndProductId(@Param("tenantId") Long tenantId,
                                               @Param("productId") Long productId);

    @Query("""
           select s from Stock s
           where s.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(s.product.productName) like lower(concat('%', cast(:search as string), '%'))
                  or lower(s.product.productCode) like lower(concat('%', cast(:search as string), '%')))
             and (:lowStockOnly = false
                  or s.quantityOnHand <= s.product.reorderLevel)
           """)
    Page<Stock> search(@Param("tenantId") Long tenantId,
                       @Param("search") String search,
                       @Param("lowStockOnly") boolean lowStockOnly,
                       Pageable pageable);

    /** CR-053 backlog item 5 (low-stock reminder job). Same predicate as search()'s lowStockOnly flag, non-paged for the scheduled job's own use. */
    @Query("select count(s) from Stock s where s.tenant.id = :tenantId and s.quantityOnHand <= s.product.reorderLevel")
    long countLowStock(@Param("tenantId") Long tenantId);
}
