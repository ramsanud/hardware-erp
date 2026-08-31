package com.hardware.erp.analytics.repository;

import com.hardware.erp.invoice.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Aggregation queries for the analytics dashboard (CR-048).
 *
 * Every one of these is a GROUP BY executed in PostgreSQL, never a full table
 * read aggregated in Java or the browser. A shop with three years of invoices
 * has tens of thousands of rows and hundreds of thousands of line items;
 * shipping those to the client to sum them would be slow now and unusable
 * later.
 *
 * TENANT ISOLATION, and the thing that makes it easy to get wrong here:
 * invoice_item and payment carry no tenant_id of their own - invoice_item has
 * none at all, and payment's is denormalised. Both are therefore scoped by
 * joining invoice and filtering invoice.tenant_id. Every query below does
 * that explicitly. A missing join here would leak one shop's sales into
 * another shop's charts, which is the worst failure this module could have.
 *
 * CANCELLED invoices are excluded everywhere, matching the convention
 * InvoiceRepository already uses for customer totals - a cancelled invoice
 * carries no financial weight and must not appear in revenue.
 *
 * Native rather than JPQL because these need date_trunc, width_bucket and
 * extract, which JPQL cannot express.
 */
public interface AnalyticsRepository extends JpaRepository<Invoice, Long> {

    // ---------------------------------------------------------------- KPIs

    interface SummaryRow {
        Long getInvoiceCount();
        Long getRevenuePaise();
        Long getOutstandingPaise();
    }

    @Query(value = """
           select count(*)                          as "invoiceCount",
                  coalesce(sum(total_paise), 0)     as "revenuePaise",
                  coalesce(sum(balance_paise), 0)   as "outstandingPaise"
           from invoice
           where tenant_id = :tenantId
             and status <> 'CANCELLED'
             and invoice_date between :from and :to
           """, nativeQuery = true)
    SummaryRow summary(@Param("tenantId") Long tenantId,
                       @Param("from") LocalDate from,
                       @Param("to") LocalDate to);

    // -------------------------------------------------- revenue trend (line)

    interface TrendRow {
        String getBucket();
        Long getRevenuePaise();
        Long getInvoiceCount();
    }

    /**
     * GROUP BY 1, not a repeat of the expression: an untyped bind parameter
     * makes PostgreSQL treat the two date_trunc calls as different
     * expressions, and it then demands invoice_date in the GROUP BY. The cast
     * pins the parameter's type; grouping by position keeps the two in step.
     *
     * No SQL line comments inside this string - Spring Data's own query parser
     * reads it before PostgreSQL does and fails on them.
     *
     * {@code :granularity} is a bound parameter, never concatenated. The
     * service restricts it to day/week/month first, so this is belt and
     * braces rather than the only guard.
     */
    @Query(value = """
           select to_char(date_trunc(cast(:granularity as text), cast(invoice_date as timestamp)), 'YYYY-MM-DD') as "bucket",
                  coalesce(sum(total_paise), 0) as "revenuePaise",
                  count(*) as "invoiceCount"
           from invoice
           where tenant_id = :tenantId
             and status <> 'CANCELLED'
             and invoice_date between :from and :to
           group by 1
           order by 1
           """, nativeQuery = true)
    List<TrendRow> revenueTrend(@Param("tenantId") Long tenantId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to,
                                @Param("granularity") String granularity);

    // ------------------------------------------------ sales by category (bar)

    interface LabelledAmountRow {
        String getLabel();
        Long getAmountPaise();
        Long getQuantity();
    }

    @Query(value = """
           select coalesce(c.category_name, 'Uncategorised') as "label",
                  coalesce(sum(ii.line_total_paise), 0)      as "amountPaise",
                  coalesce(sum(ii.quantity), 0)              as "quantity"
           from invoice_item ii
             join invoice i  on i.invoice_id = ii.invoice_id
             join product p  on p.product_id = ii.product_id
             left join category c on c.category_id = p.category_id
           where i.tenant_id = :tenantId
             and i.status <> 'CANCELLED'
             and i.invoice_date between :from and :to
           group by c.category_name
           order by 2 desc
           """, nativeQuery = true)
    List<LabelledAmountRow> salesByCategory(@Param("tenantId") Long tenantId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    // ------------------------------------- top products (bar + scatter source)

    interface ProductPerformanceRow {
        Long getProductId();
        String getLabel();
        Long getAmountPaise();
        java.math.BigDecimal getQuantity();
        Long getUnitPricePaise();
    }

    /**
     * Doubles as the scatter plot's source: it returns unit price alongside
     * quantity sold, which is the only pair in this schema where a
     * correlation is actually meaningful (does cheaper stock move faster).
     * One query rather than two so the two charts cannot disagree.
     */
    @Query(value = """
           select ii.product_id                          as "productId",
                  max(ii.product_name_snapshot)          as "label",
                  coalesce(sum(ii.line_total_paise), 0)  as "amountPaise",
                  coalesce(sum(ii.quantity), 0)          as "quantity",
                  round(avg(ii.unit_price_paise))        as "unitPricePaise"
           from invoice_item ii
             join invoice i on i.invoice_id = ii.invoice_id
           where i.tenant_id = :tenantId
             and i.status <> 'CANCELLED'
             and i.invoice_date between :from and :to
           group by ii.product_id
           order by 3 desc
           limit :limit
           """, nativeQuery = true)
    List<ProductPerformanceRow> topProducts(@Param("tenantId") Long tenantId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to,
                                            @Param("limit") int limit);

    // ------------------------------------------- payment methods (pie/donut)

    @Query(value = """
           select p.payment_method                    as "label",
                  coalesce(sum(p.amount_paise), 0)    as "amountPaise",
                  count(*)                            as "quantity"
           from payment p
             join invoice i on i.invoice_id = p.invoice_id
           where i.tenant_id = :tenantId
             and i.status <> 'CANCELLED'
             and p.payment_date::date between :from and :to
           group by p.payment_method
           order by 2 desc
           """, nativeQuery = true)
    List<LabelledAmountRow> paymentMethods(@Param("tenantId") Long tenantId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    // ------------------------------------------ invoice status (pie/donut)

    @Query(value = """
           select status                          as "label",
                  coalesce(sum(total_paise), 0)   as "amountPaise",
                  count(*)                        as "quantity"
           from invoice
           where tenant_id = :tenantId
             and invoice_date between :from and :to
           group by status
           order by 2 desc
           """, nativeQuery = true)
    List<LabelledAmountRow> invoiceStatusSplit(@Param("tenantId") Long tenantId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    // ---------------------------------- invoice value distribution (histogram)

    interface BucketRow {
        Integer getBucket();
        Long getInvoiceCount();
    }

    /**
     * width_bucket does the binning in PostgreSQL. The alternative - selecting
     * every invoice total and binning them in Java - would transfer the whole
     * period's invoices just to count them.
     *
     * Bucket 0 is below the floor and bucket count+1 is above the ceiling;
     * both are returned and the service labels them as overflow bins rather
     * than silently dropping the outliers, which on a hardware shop's data
     * are the large project invoices - the ones most worth seeing.
     */
    @Query(value = """
           select width_bucket(total_paise, :floorPaise, :ceilingPaise, :buckets) as "bucket",
                  count(*)                                                        as "invoiceCount"
           from invoice
           where tenant_id = :tenantId
             and status <> 'CANCELLED'
             and invoice_date between :from and :to
           group by 1
           order by 1
           """, nativeQuery = true)
    List<BucketRow> invoiceValueDistribution(@Param("tenantId") Long tenantId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to,
                                             @Param("floorPaise") long floorPaise,
                                             @Param("ceilingPaise") long ceilingPaise,
                                             @Param("buckets") int buckets);

    // ----------------------------------------------- sales activity (heatmap)

    interface ActivityRow {
        Integer getDayOfWeek();
        Integer getHourOfDay();
        Long getInvoiceCount();
        Long getRevenuePaise();
    }

    /**
     * Uses created_at, not invoice_date: invoice_date is a DATE and carries no
     * time, so it cannot answer "which hour is the counter busiest". created_at
     * is the moment the invoice was actually raised, which is the honest
     * source for this question.
     *
     * dow is 0=Sunday..6=Saturday, PostgreSQL's own convention; the frontend
     * maps it to labels rather than this query hardcoding day names.
     */
    @Query(value = """
           select extract(dow  from i.created_at)::int   as "dayOfWeek",
                  extract(hour from i.created_at)::int   as "hourOfDay",
                  count(*)                               as "invoiceCount",
                  coalesce(sum(i.total_paise), 0)        as "revenuePaise"
           from invoice i
           where i.tenant_id = :tenantId
             and i.status <> 'CANCELLED'
             and i.invoice_date between :from and :to
           group by 1, 2
           order by 1, 2
           """, nativeQuery = true)
    List<ActivityRow> salesActivity(@Param("tenantId") Long tenantId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);
}
