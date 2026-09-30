package com.hardware.erp.summary;

import com.hardware.erp.invoice.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

/** CR-092. The day's figures for one shop, every one a count or a sum over recorded rows. */
public interface DailySummaryRepository extends JpaRepository<Invoice, Long> {

    interface DayRow {
        Long getInvoiceCount();
        Long getSalesPaise();
        Long getPaymentsPaise();
        Long getPurchaseCount();
        Long getPurchasesPaise();
        Long getOutstandingPaise();
        Long getLowStockCount();
    }

    @Query(value = """
            SELECT
              (SELECT COUNT(*) FROM invoice i WHERE i.tenant_id = :tenantId AND i.status <> 'CANCELLED' AND i.invoice_date = :day) AS invoiceCount,
              (SELECT COALESCE(SUM(i.total_paise), 0) FROM invoice i WHERE i.tenant_id = :tenantId AND i.status <> 'CANCELLED' AND i.invoice_date = :day) AS salesPaise,
              (SELECT COALESCE(SUM(p.amount_paise), 0) FROM payment p WHERE p.tenant_id = :tenantId AND CAST(p.payment_date AS DATE) = :day) AS paymentsPaise,
              (SELECT COUNT(*) FROM purchase pu WHERE pu.tenant_id = :tenantId AND pu.status <> 'CANCELLED' AND pu.purchase_date = :day) AS purchaseCount,
              (SELECT COALESCE(SUM(pu.total_paise), 0) FROM purchase pu WHERE pu.tenant_id = :tenantId AND pu.status <> 'CANCELLED' AND pu.purchase_date = :day) AS purchasesPaise,
              (SELECT COALESCE(SUM(i.balance_paise), 0) FROM invoice i WHERE i.tenant_id = :tenantId AND i.status IN ('UNPAID', 'PARTIALLY_PAID')) AS outstandingPaise,
              (SELECT COUNT(*) FROM stock s JOIN product p ON p.product_id = s.product_id
                 WHERE s.tenant_id = :tenantId AND p.deleted_at IS NULL AND p.status = 'ACTIVE'
                   AND p.minimum_stock > 0 AND s.quantity_on_hand <= p.minimum_stock) AS lowStockCount
            """, nativeQuery = true)
    DayRow day(@Param("tenantId") Long tenantId, @Param("day") LocalDate day);
}
