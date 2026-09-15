package com.hardware.erp.report.repository;

import com.hardware.erp.invoice.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Aggregation queries behind the five operational reports (CR-086).
 *
 * The same discipline as AnalyticsRepository: every figure is a GROUP BY in
 * PostgreSQL, never rows pulled into Java and summed; every child table
 * (invoice_item, purchase_item, credit_note_item) is scoped by joining its
 * parent and filtering the parent's tenant_id, because the children carry
 * none of their own; CANCELLED documents carry no financial weight and are
 * excluded everywhere.
 *
 * Inter-state is decided the way InvoicePdfService decides it - party state
 * differs from shop state, both known - and returned as a flag so the
 * CGST/SGST vs IGST split happens in exactly one place (ReportServiceImpl).
 */
public interface ReportRepository extends JpaRepository<Invoice, Long> {

    // ------------------------------------------------------------ Day Book

    interface VoucherRow {
        LocalDate getDate();
        String getReference();
        String getParty();
        String getDetail();
        Long getAmountPaise();
    }

    @Query(value = """
           select i.invoice_date   as "date",
                  i.invoice_number as "reference",
                  c.customer_name  as "party",
                  i.status         as "detail",
                  i.total_paise    as "amountPaise"
           from invoice i
           join customer c on c.customer_id = i.customer_id
           where i.tenant_id = :tenantId
             and i.status <> 'CANCELLED'
             and i.invoice_date between :from and :to
           order by i.invoice_date, i.invoice_id
           """, nativeQuery = true)
    List<VoucherRow> dayBookSales(@Param("tenantId") Long tenantId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    @Query(value = """
           select cast(p.payment_date as date) as "date",
                  i.invoice_number             as "reference",
                  c.customer_name              as "party",
                  p.payment_method             as "detail",
                  p.amount_paise               as "amountPaise"
           from payment p
           join invoice i on i.invoice_id = p.invoice_id
           join customer c on c.customer_id = i.customer_id
           where i.tenant_id = :tenantId
             and cast(p.payment_date as date) between :from and :to
           order by p.payment_date, p.payment_id
           """, nativeQuery = true)
    List<VoucherRow> dayBookReceipts(@Param("tenantId") Long tenantId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    @Query(value = """
           select n.credit_note_date   as "date",
                  n.credit_note_number as "reference",
                  c.customer_name      as "party",
                  n.reason             as "detail",
                  n.total_paise        as "amountPaise"
           from credit_note n
           join customer c on c.customer_id = n.customer_id
           where n.tenant_id = :tenantId
             and n.status <> 'CANCELLED'
             and n.credit_note_date between :from and :to
           order by n.credit_note_date, n.credit_note_id
           """, nativeQuery = true)
    List<VoucherRow> dayBookCreditNotes(@Param("tenantId") Long tenantId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    @Query(value = """
           select p.purchase_date   as "date",
                  p.purchase_number as "reference",
                  s.supplier_name   as "party",
                  p.status          as "detail",
                  p.total_paise     as "amountPaise"
           from purchase p
           join supplier s on s.supplier_id = p.supplier_id
           where p.tenant_id = :tenantId
             and p.status not in ('CANCELLED', 'DRAFT')
             and p.purchase_date between :from and :to
           order by p.purchase_date, p.purchase_id
           """, nativeQuery = true)
    List<VoucherRow> dayBookPurchases(@Param("tenantId") Long tenantId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    @Query(value = """
           select e.expense_date                          as "date",
                  cast(e.business_expense_id as varchar)  as "reference",
                  k.name                                  as "party",
                  e.payment_method                        as "detail",
                  e.amount_paise                          as "amountPaise"
           from business_expense e
           join expense_category k on k.expense_category_id = e.category_id
           where e.tenant_id = :tenantId
             and e.status <> 'CANCELLED'
             and e.expense_date between :from and :to
           order by e.expense_date, e.business_expense_id
           """, nativeQuery = true)
    List<VoucherRow> dayBookExpenses(@Param("tenantId") Long tenantId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    // -------------------------------------------------- Receivables Ageing

    interface AgeingRowView {
        Long getCustomerId();
        String getCustomerName();
        String getMobileNo();
        Long getBucket0();
        Long getBucket31();
        Long getBucket61();
        Long getBucket91();
        Long getOpenInvoices();
    }

    /**
     * Age is counted from the invoice date, not a due date - the invoice
     * table has none. {@code :asOf - invoice_date} is PostgreSQL date
     * arithmetic yielding whole days.
     */
    @Query(value = """
           select c.customer_id   as "customerId",
                  c.customer_name as "customerName",
                  c.mobile_no     as "mobileNo",
                  coalesce(sum(case when (cast(:asOf as date) - i.invoice_date) <= 30 then i.balance_paise else 0 end), 0)                                    as "bucket0",
                  coalesce(sum(case when (cast(:asOf as date) - i.invoice_date) between 31 and 60 then i.balance_paise else 0 end), 0)                        as "bucket31",
                  coalesce(sum(case when (cast(:asOf as date) - i.invoice_date) between 61 and 90 then i.balance_paise else 0 end), 0)                        as "bucket61",
                  coalesce(sum(case when (cast(:asOf as date) - i.invoice_date) > 90 then i.balance_paise else 0 end), 0)                                     as "bucket91",
                  count(*)                                                                                                                     as "openInvoices"
           from invoice i
           join customer c on c.customer_id = i.customer_id
           where i.tenant_id = :tenantId
             and i.status in ('UNPAID', 'PARTIALLY_PAID')
             and i.balance_paise > 0
             and i.invoice_date <= :asOf
           group by c.customer_id, c.customer_name, c.mobile_no
           order by sum(i.balance_paise) desc, c.customer_name
           """, nativeQuery = true)
    List<AgeingRowView> receivablesAgeing(@Param("tenantId") Long tenantId,
                                          @Param("asOf") LocalDate asOf);

    // ------------------------------------------------------ Stock Valuation

    interface StockRowView {
        Long getProductId();
        String getProductCode();
        String getProductName();
        String getCategoryName();
        String getUnit();
        BigDecimal getQuantityOnHand();
        Long getPurchasePricePaise();
        Long getSellingPricePaise();
    }

    @Query(value = """
           select p.product_id          as "productId",
                  p.product_code        as "productCode",
                  p.product_name        as "productName",
                  k.category_name       as "categoryName",
                  p.unit                as "unit",
                  s.quantity_on_hand    as "quantityOnHand",
                  p.purchase_price_paise as "purchasePricePaise",
                  p.selling_price_paise  as "sellingPricePaise"
           from stock s
           join product p on p.product_id = s.product_id
           left join category k on k.category_id = p.category_id
           where s.tenant_id = :tenantId
             and p.deleted_at is null
             and s.quantity_on_hand <> 0
           order by (s.quantity_on_hand * p.purchase_price_paise) desc, p.product_name
           """, nativeQuery = true)
    List<StockRowView> stockValuation(@Param("tenantId") Long tenantId);

    // ---------------------------------------------------- Purchase Register

    interface PurchaseRowView {
        Long getPurchaseId();
        LocalDate getPurchaseDate();
        String getPurchaseNumber();
        String getSupplierBillNumber();
        String getSupplierName();
        String getSupplierGstNo();
        String getStatus();
        Long getSubtotalPaise();
        Long getGstAmountPaise();
        Long getTotalPaise();
        Long getPaidPaise();
        Long getBalancePaise();
        Boolean getInterState();
    }

    @Query(value = """
           select p.purchase_id          as "purchaseId",
                  p.purchase_date        as "purchaseDate",
                  p.purchase_number      as "purchaseNumber",
                  p.supplier_bill_number as "supplierBillNumber",
                  s.supplier_name        as "supplierName",
                  s.gst_no               as "supplierGstNo",
                  p.status               as "status",
                  p.subtotal_paise       as "subtotalPaise",
                  p.gst_amount_paise     as "gstAmountPaise",
                  p.total_paise          as "totalPaise",
                  p.paid_paise           as "paidPaise",
                  p.balance_paise        as "balancePaise",
                  (s.state_code is not null and t.state_code is not null and s.state_code <> t.state_code) as "interState"
           from purchase p
           join supplier s on s.supplier_id = p.supplier_id
           join tenant t on t.tenant_id = p.tenant_id
           where p.tenant_id = :tenantId
             and p.status not in ('CANCELLED', 'DRAFT')
             and p.purchase_date between :from and :to
           order by p.purchase_date, p.purchase_id
           """, nativeQuery = true)
    List<PurchaseRowView> purchaseRegister(@Param("tenantId") Long tenantId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    // ---------------------------------------------------------- GST Summary

    interface RateSlabView {
        BigDecimal getRatePercent();
        Boolean getInterState();
        Long getTaxablePaise();
        Long getGstPaise();
    }

    @Query(value = """
           select ii.gst_rate_percent as "ratePercent",
                  (c.state_code is not null and t.state_code is not null and c.state_code <> t.state_code) as "interState",
                  coalesce(sum(ii.line_subtotal_paise), 0) as "taxablePaise",
                  coalesce(sum(ii.line_gst_paise), 0)      as "gstPaise"
           from invoice_item ii
           join invoice i on i.invoice_id = ii.invoice_id
           join customer c on c.customer_id = i.customer_id
           join tenant t on t.tenant_id = i.tenant_id
           where i.tenant_id = :tenantId
             and i.status <> 'CANCELLED'
             and i.invoice_date between :from and :to
           group by ii.gst_rate_percent, 2
           order by ii.gst_rate_percent, 2
           """, nativeQuery = true)
    List<RateSlabView> outwardByRate(@Param("tenantId") Long tenantId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    @Query(value = """
           select ci.gst_rate_percent as "ratePercent",
                  (c.state_code is not null and t.state_code is not null and c.state_code <> t.state_code) as "interState",
                  coalesce(sum(ci.line_subtotal_paise), 0) as "taxablePaise",
                  coalesce(sum(ci.line_gst_paise), 0)      as "gstPaise"
           from credit_note_item ci
           join credit_note n on n.credit_note_id = ci.credit_note_id
           join customer c on c.customer_id = n.customer_id
           join tenant t on t.tenant_id = n.tenant_id
           where n.tenant_id = :tenantId
             and n.status <> 'CANCELLED'
             and n.credit_note_date between :from and :to
           group by ci.gst_rate_percent, 2
           order by ci.gst_rate_percent, 2
           """, nativeQuery = true)
    List<RateSlabView> creditNotesByRate(@Param("tenantId") Long tenantId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    @Query(value = """
           select pi.gst_rate_percent as "ratePercent",
                  (s.state_code is not null and t.state_code is not null and s.state_code <> t.state_code) as "interState",
                  coalesce(sum(pi.line_subtotal_paise), 0) as "taxablePaise",
                  coalesce(sum(pi.line_gst_paise), 0)      as "gstPaise"
           from purchase_item pi
           join purchase p on p.purchase_id = pi.purchase_id
           join supplier s on s.supplier_id = p.supplier_id
           join tenant t on t.tenant_id = p.tenant_id
           where p.tenant_id = :tenantId
             and p.status not in ('CANCELLED', 'DRAFT')
             and p.purchase_date between :from and :to
           group by pi.gst_rate_percent, 2
           order by pi.gst_rate_percent, 2
           """, nativeQuery = true)
    List<RateSlabView> inwardByRate(@Param("tenantId") Long tenantId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);
}
