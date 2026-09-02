package com.hardware.erp.invoice.repository;

import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
           select i from Invoice i
           where i.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(i.invoiceNumber) like lower(concat('%', cast(:search as string), '%'))
                  or lower(i.customer.customerName) like lower(concat('%', cast(:search as string), '%'))
                  or i.customer.mobileNo like concat('%', cast(:search as string), '%'))
             and (:status is null or i.status = :status)
             and (:fromDate is null or i.invoiceDate >= :fromDate)
             and (:toDate is null or i.invoiceDate <= :toDate)
           order by i.invoiceDate desc, i.id desc
           """)
    Page<Invoice> search(@Param("tenantId") Long tenantId,
                         @Param("search") String search,
                         @Param("status") InvoiceStatus status,
                         @Param("fromDate") LocalDate fromDate,
                         @Param("toDate") LocalDate toDate,
                         Pageable pageable);


    /** Cancelled invoices are excluded - they carry no real financial weight. */
    @Query("""
           select count(i), coalesce(sum(i.totalPaise), 0), coalesce(sum(i.paidPaise), 0), coalesce(sum(i.balancePaise), 0)
           from Invoice i
           where i.tenant.id = :tenantId and i.customer.id = :customerId and i.status <> 'CANCELLED'
           """)
    List<Object[]> customerFinancialSummary(@Param("tenantId") Long tenantId, @Param("customerId") Long customerId);

    @Query("""
           select i from Invoice i
           where i.tenant.id = :tenantId and i.customer.id = :customerId
           order by i.invoiceDate desc, i.id desc
           """)
    Page<Invoice> findByCustomer(@Param("tenantId") Long tenantId, @Param("customerId") Long customerId, Pageable pageable);

    /**
     * Customer 360 - what this customer has bought before, so the shop
     * never has to remember it by hand (CR-030 request §6). "Last price"
     * is a correlated subquery for the most recent invoice line for that
     * product, not an average - matches what the customer actually paid
     * last time, which is what a counter clerk re-quoting them cares
     * about. Cancelled invoices excluded, same as every other customer
     * aggregate in this file.
     */
    @Query(value = """
           select ii.product_id, p.product_name, p.product_code, ii.unit,
                  sum(ii.quantity) as total_quantity,
                  (select ii2.unit_price_paise from invoice_item ii2
                   join invoice i2 on i2.invoice_id = ii2.invoice_id
                   where ii2.product_id = ii.product_id and i2.tenant_id = :tenantId
                     and i2.customer_id = :customerId and i2.status <> 'CANCELLED'
                   order by i2.invoice_date desc, i2.invoice_id desc limit 1) as last_price_paise,
                  max(i.invoice_date) as last_purchase_date
           from invoice_item ii
           join invoice i on i.invoice_id = ii.invoice_id
           join product p on p.product_id = ii.product_id
           where i.tenant_id = :tenantId and i.customer_id = :customerId and i.status <> 'CANCELLED'
           group by ii.product_id, p.product_name, p.product_code, ii.unit
           order by max(i.invoice_date) desc
           """, nativeQuery = true)
    List<Object[]> productPurchaseHistory(@Param("tenantId") Long tenantId, @Param("customerId") Long customerId);

    long countByTenantIdAndCustomerId(Long tenantId, Long customerId);

    /** Cancelled invoices excluded, same as customerFinancialSummary - they carry no real financial weight. */
    @Query("""
           select coalesce(sum(i.totalPaise), 0), coalesce(sum(i.balancePaise), 0)
           from Invoice i
           where i.tenant.id = :tenantId and i.status <> 'CANCELLED'
           """)
    List<Object[]> tenantSalesSummary(@Param("tenantId") Long tenantId);

    @Query("""
           select coalesce(sum(i.totalPaise), 0)
           from Invoice i
           where i.tenant.id = :tenantId and i.status <> 'CANCELLED' and i.invoiceDate = :today
           """)
    long todaySales(@Param("tenantId") Long tenantId, @Param("today") LocalDate today);

    /** CR-053 backlog item 2 (Tally export). Cancelled invoices excluded - a cancelled sale never really happened. */
    @Query("""
           select i from Invoice i
           where i.tenant.id = :tenantId and i.status <> 'CANCELLED'
             and i.invoiceDate >= :fromDate and i.invoiceDate <= :toDate
           order by i.invoiceDate asc, i.id asc
           """)
    List<Invoice> findForExport(@Param("tenantId") Long tenantId,
                                @Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate);

    /** CR-053 backlog item 5 (payment-due reminder job). */
    @Query("""
           select count(i), coalesce(sum(i.balancePaise), 0)
           from Invoice i
           where i.tenant.id = :tenantId and i.status <> 'CANCELLED' and i.balancePaise > 0
           """)
    List<Object[]> outstandingSummary(@Param("tenantId") Long tenantId);
}
