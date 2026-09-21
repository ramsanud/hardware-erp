package com.hardware.erp.branch.repository;

import com.hardware.erp.branch.entity.BranchStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BranchStockRepository extends JpaRepository<BranchStock, Long> {

    /**
     * The one write path. Upsert so a branch's first movement of a product
     * needs no prior row; the caller decides what that first row starts from
     * (see StockServiceImpl.recordBranchDelta) by passing it as {@code seed}.
     */
    @Modifying
    @Query(value = """
            INSERT INTO branch_stock (tenant_id, branch_id, product_id, quantity_on_hand, updated_at)
            VALUES (:tenantId, :branchId, :productId, :seed + :delta, now())
            ON CONFLICT (branch_id, product_id)
            DO UPDATE SET quantity_on_hand = branch_stock.quantity_on_hand + :delta, updated_at = now()
            """, nativeQuery = true)
    void addQuantity(@Param("tenantId") Long tenantId, @Param("branchId") Long branchId,
                     @Param("productId") Long productId, @Param("seed") BigDecimal seed,
                     @Param("delta") BigDecimal delta);

    /**
     * The moment a shop gets its second branch, every unit it holds is by
     * definition at MAIN - the only branch there has ever been. Snapshot
     * that once, for every product without a MAIN row (V63 covered rows that
     * existed at migration time; this covers stock created since by seed or
     * direct SQL). After this, an absent row honestly means zero.
     */
    @Modifying
    @Query(value = """
            INSERT INTO branch_stock (tenant_id, branch_id, product_id, quantity_on_hand, updated_at)
            SELECT s.tenant_id, :mainBranchId, s.product_id, s.quantity_on_hand, now()
            FROM stock s WHERE s.tenant_id = :tenantId
            ON CONFLICT (branch_id, product_id) DO NOTHING
            """, nativeQuery = true)
    int snapshotMainFromShopStock(@Param("tenantId") Long tenantId, @Param("mainBranchId") Long mainBranchId);

    boolean existsByBranchIdAndProductId(Long branchId, Long productId);

    Optional<BranchStock> findByBranchIdAndProductId(Long branchId, Long productId);

    interface BranchStockRow {
        Long getBranchId();
        String getBranchName();
        Long getProductId();
        String getProductCode();
        String getProductName();
        String getUnit();
        BigDecimal getQuantityOnHand();
    }

    @Query(value = """
            SELECT bs.branch_id AS branchId, b.branch_name AS branchName,
                   p.product_id AS productId, p.product_code AS productCode, p.product_name AS productName,
                   p.unit AS unit, bs.quantity_on_hand AS quantityOnHand
            FROM branch_stock bs
            JOIN branch b ON b.branch_id = bs.branch_id
            JOIN product p ON p.product_id = bs.product_id
            WHERE bs.tenant_id = :tenantId
              AND (CAST(:branchId AS BIGINT) IS NULL OR bs.branch_id = CAST(:branchId AS BIGINT))
              AND (CAST(:search AS VARCHAR) IS NULL
                   OR p.product_name ILIKE '%' || CAST(:search AS VARCHAR) || '%'
                   OR p.product_code ILIKE '%' || CAST(:search AS VARCHAR) || '%')
              AND bs.quantity_on_hand <> 0
            ORDER BY b.is_main DESC, b.branch_name, p.product_name
            LIMIT 500
            """, nativeQuery = true)
    List<BranchStockRow> breakdown(@Param("tenantId") Long tenantId, @Param("branchId") Long branchId,
                                   @Param("search") String search);
}
