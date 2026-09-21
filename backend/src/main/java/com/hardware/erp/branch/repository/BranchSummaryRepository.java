package com.hardware.erp.branch.repository;

import com.hardware.erp.branch.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * CR-092. Branch-wise figures, every one a count or a sum over rows that
 * carry a branch_id. Cancelled documents are excluded from money, the same
 * rule AnalyticsRepository applies for the shop as a whole.
 */
public interface BranchSummaryRepository extends JpaRepository<Branch, Long> {

    interface BranchSummaryRow {
        Long getBranchId();
        String getBranchCode();
        String getBranchName();
        Boolean getMain();
        Long getInvoiceCount();
        Long getSalesPaise();
        Long getPurchaseCount();
        Long getPurchasesPaise();
        Long getUserCount();
        Long getProductsInStock();
    }

    @Query(value = """
            SELECT b.branch_id AS branchId, b.branch_code AS branchCode, b.branch_name AS branchName, b.is_main AS main,
                   (SELECT COUNT(*) FROM invoice i WHERE i.branch_id = b.branch_id AND i.status <> 'CANCELLED'
                       AND i.invoice_date BETWEEN :from AND :to) AS invoiceCount,
                   (SELECT COALESCE(SUM(i.total_paise), 0) FROM invoice i WHERE i.branch_id = b.branch_id AND i.status <> 'CANCELLED'
                       AND i.invoice_date BETWEEN :from AND :to) AS salesPaise,
                   (SELECT COUNT(*) FROM purchase p WHERE p.branch_id = b.branch_id AND p.status <> 'CANCELLED'
                       AND p.purchase_date BETWEEN :from AND :to) AS purchaseCount,
                   (SELECT COALESCE(SUM(p.total_paise), 0) FROM purchase p WHERE p.branch_id = b.branch_id AND p.status <> 'CANCELLED'
                       AND p.purchase_date BETWEEN :from AND :to) AS purchasesPaise,
                   (SELECT COUNT(*) FROM app_user u WHERE u.branch_id = b.branch_id AND u.deleted_at IS NULL) AS userCount,
                   (SELECT COUNT(*) FROM branch_stock bs WHERE bs.branch_id = b.branch_id AND bs.quantity_on_hand > 0) AS productsInStock
            FROM branch b
            WHERE b.tenant_id = :tenantId
            ORDER BY b.is_main DESC, b.branch_name
            """, nativeQuery = true)
    List<BranchSummaryRow> summary(@Param("tenantId") Long tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
