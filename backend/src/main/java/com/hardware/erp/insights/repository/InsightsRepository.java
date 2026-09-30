package com.hardware.erp.insights.repository;

import com.hardware.erp.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * CR-092. Native, read-only, tenant-scoped. Cancelled invoices are
 * excluded everywhere, matching AnalyticsRepository. Every column is a
 * count or a sum over recorded rows - no estimate is ever produced here.
 */
public interface InsightsRepository extends JpaRepository<Product, Long> {

    interface ProductSalesRow {
        Long getProductId();
        String getProductCode();
        String getProductName();
        String getUnit();
        BigDecimal getQuantityOnHand();
        BigDecimal getQuantitySold();
        LocalDate getLastSoldOn();
        Long getAverageCostPaise();
        BigDecimal getReorderLevel();
        Long getSellingPricePaise();
        Long getSubtotalPaise();
    }

    /**
     * One row per active product with stock: what it sold in the window,
     * when it last sold at all (any time), and its cost basis. The single
     * query behind slow-moving, overstock and reorder.
     */
    @Query(value = """
            SELECT p.product_id AS productId, p.product_code AS productCode, p.product_name AS productName, p.unit AS unit,
                   COALESCE(s.quantity_on_hand, 0) AS quantityOnHand,
                   COALESCE(w.qty, 0) AS quantitySold,
                   ls.last_sold AS lastSoldOn,
                   COALESCE(s.average_cost_paise, p.purchase_price_paise, 0) AS averageCostPaise,
                   p.reorder_level AS reorderLevel,
                   p.selling_price_paise AS sellingPricePaise,
                   COALESCE(w.subtotal, 0) AS subtotalPaise
            FROM product p
            LEFT JOIN stock s ON s.tenant_id = p.tenant_id AND s.product_id = p.product_id
            LEFT JOIN (
                SELECT ii.product_id, SUM(ii.quantity + COALESCE(ii.free_quantity, 0)) AS qty, SUM(ii.line_subtotal_paise) AS subtotal
                FROM invoice_item ii JOIN invoice i ON i.invoice_id = ii.invoice_id
                WHERE i.tenant_id = :tenantId AND i.status <> 'CANCELLED' AND i.invoice_date BETWEEN :from AND :to
                GROUP BY ii.product_id
            ) w ON w.product_id = p.product_id
            LEFT JOIN (
                SELECT ii.product_id, MAX(i.invoice_date) AS last_sold
                FROM invoice_item ii JOIN invoice i ON i.invoice_id = ii.invoice_id
                WHERE i.tenant_id = :tenantId AND i.status <> 'CANCELLED'
                GROUP BY ii.product_id
            ) ls ON ls.product_id = p.product_id
            WHERE p.tenant_id = :tenantId AND p.deleted_at IS NULL AND p.status = 'ACTIVE'
            """, nativeQuery = true)
    List<ProductSalesRow> productSales(@Param("tenantId") Long tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    interface DemandRow {
        Long getProductId();
        String getProductCode();
        String getProductName();
        String getUnit();
        BigDecimal getCurrentQuantity();
        BigDecimal getPreviousQuantity();
    }

    @Query(value = """
            SELECT p.product_id AS productId, p.product_code AS productCode, p.product_name AS productName, p.unit AS unit,
                   COALESCE(SUM(CASE WHEN i.invoice_date BETWEEN :currentFrom AND :currentTo THEN ii.quantity ELSE 0 END), 0) AS currentQuantity,
                   COALESCE(SUM(CASE WHEN i.invoice_date BETWEEN :previousFrom AND :previousTo THEN ii.quantity ELSE 0 END), 0) AS previousQuantity
            FROM invoice_item ii
            JOIN invoice i ON i.invoice_id = ii.invoice_id
            JOIN product p ON p.product_id = ii.product_id
            WHERE i.tenant_id = :tenantId AND i.status <> 'CANCELLED'
              AND i.invoice_date BETWEEN :previousFrom AND :currentTo
            GROUP BY p.product_id, p.product_code, p.product_name, p.unit
            HAVING SUM(ii.quantity) > 0
            """, nativeQuery = true)
    List<DemandRow> demand(@Param("tenantId") Long tenantId,
                           @Param("currentFrom") LocalDate currentFrom, @Param("currentTo") LocalDate currentTo,
                           @Param("previousFrom") LocalDate previousFrom, @Param("previousTo") LocalDate previousTo);

    interface PairRow {
        Long getProductAId();
        String getProductAName();
        Long getProductBId();
        String getProductBName();
        Long getInvoicesTogether();
        Long getInvoicesWithA();
    }

    /** Ordered pairs (A, B) with A's own invoice count for support; both directions are returned. */
    @Query(value = """
            WITH lines AS (
                SELECT DISTINCT i.invoice_id, ii.product_id
                FROM invoice_item ii JOIN invoice i ON i.invoice_id = ii.invoice_id
                WHERE i.tenant_id = :tenantId AND i.status <> 'CANCELLED' AND i.invoice_date BETWEEN :from AND :to
            ),
            per_product AS (SELECT product_id, COUNT(*) AS invoices FROM lines GROUP BY product_id),
            pairs AS (
                SELECT a.product_id AS product_a, b.product_id AS product_b, COUNT(*) AS together
                FROM lines a JOIN lines b ON a.invoice_id = b.invoice_id AND a.product_id <> b.product_id
                GROUP BY a.product_id, b.product_id
                HAVING COUNT(*) >= :minTogether
            )
            SELECT pr.product_a AS productAId, pa.product_name AS productAName,
                   pr.product_b AS productBId, pb.product_name AS productBName,
                   pr.together AS invoicesTogether, pp.invoices AS invoicesWithA
            FROM pairs pr
            JOIN per_product pp ON pp.product_id = pr.product_a
            JOIN product pa ON pa.product_id = pr.product_a
            JOIN product pb ON pb.product_id = pr.product_b
            ORDER BY pr.together DESC, pp.invoices DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<PairRow> boughtTogether(@Param("tenantId") Long tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to,
                                 @Param("minTogether") int minTogether, @Param("limit") int limit);

    interface RealisedRow {
        Long getProductId();
        BigDecimal getQuantity();
        Long getSubtotalPaise();
    }

    @Query(value = """
            SELECT ii.product_id AS productId, SUM(ii.quantity) AS quantity, SUM(ii.line_subtotal_paise) AS subtotalPaise
            FROM invoice_item ii JOIN invoice i ON i.invoice_id = ii.invoice_id
            WHERE i.tenant_id = :tenantId AND i.status <> 'CANCELLED' AND i.invoice_date BETWEEN :from AND :to
            GROUP BY ii.product_id
            """, nativeQuery = true)
    List<RealisedRow> realised(@Param("tenantId") Long tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
