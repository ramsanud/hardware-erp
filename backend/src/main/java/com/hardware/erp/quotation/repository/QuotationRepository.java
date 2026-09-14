package com.hardware.erp.quotation.repository;

import com.hardware.erp.quotation.entity.Quotation;
import com.hardware.erp.quotation.entity.QuotationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    Optional<Quotation> findByIdAndTenantId(Long id, Long tenantId);

    /** Platform Admin tenant data export (CR-057 phase 11). */
    List<Quotation> findByTenantId(Long tenantId);

    /**
     * CR-083 / BUG-BE-005. EXPIRED is computed from validUntil and never stored
     * (CR-022), so a plain {@code q.status = :status} could not match it - the
     * Expired filter returned an empty page from the day it shipped. The
     * service now translates the requested status into three parameters:
     * {@code status} for the stored value, {@code expiredOnly} for the
     * computed one, and {@code liveOnly} so that Draft/Sent/Accepted mean the
     * ones the badge still calls Draft/Sent/Accepted - the pill and the badge
     * must agree. REJECTED and CONVERTED pass through untouched.
     */
    @Query("""
           select q from Quotation q
           where q.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(q.quotationNumber) like lower(concat('%', cast(:search as string), '%'))
                  or lower(q.customer.customerName) like lower(concat('%', cast(:search as string), '%'))
                  or q.customer.mobileNo like concat('%', cast(:search as string), '%'))
             and (:status is null or q.status = :status)
             and (:expiredOnly = false
                  or (q.validUntil < :today
                      and q.status in (com.hardware.erp.quotation.entity.QuotationStatus.DRAFT,
                                       com.hardware.erp.quotation.entity.QuotationStatus.SENT,
                                       com.hardware.erp.quotation.entity.QuotationStatus.ACCEPTED)))
             and (:liveOnly = false or q.validUntil >= :today)
             and (:fromDate is null or q.quotationDate >= :fromDate)
             and (:toDate is null or q.quotationDate <= :toDate)
           order by q.quotationDate desc, q.id desc
           """)
    Page<Quotation> search(@Param("tenantId") Long tenantId,
                            @Param("search") String search,
                            @Param("status") QuotationStatus status,
                            @Param("expiredOnly") boolean expiredOnly,
                            @Param("liveOnly") boolean liveOnly,
                            @Param("today") LocalDate today,
                            @Param("fromDate") LocalDate fromDate,
                            @Param("toDate") LocalDate toDate,
                            Pageable pageable);

    /** One row per (stored status, computed expiry) for the CR-083 KPI cards. */
    interface StatsRow {
        String getStatus();
        Boolean getExpired();
        Long getCount();
        Long getTotalPaise();
    }

    /**
     * Native, like AnalyticsRepository.summary: JPQL cannot group by a case
     * expression cleanly, and the cards want the computed-expiry split in one
     * round trip rather than one query per card. The casts pin the parameter
     * types so PostgreSQL accepts a null search or date - an untyped null
     * bind fails with "could not determine data type of parameter".
     *
     * GROUP BY 1, 2 for the same reason as the analytics trend query: with a
     * bound parameter inside it, PostgreSQL does not recognise the expiry
     * expression in the select list and the one in GROUP BY as the same
     * expression, and demands valid_until be grouped instead.
     */
    @Query(value = """
           select q.status                              as "status",
                  (q.valid_until < cast(:today as date)) as "expired",
                  count(*)                              as "count",
                  coalesce(sum(q.total_paise), 0)       as "totalPaise"
           from quotation q
           join customer c on c.customer_id = q.customer_id
           where q.tenant_id = :tenantId
             and (cast(:search as text) is null
                  or lower(q.quotation_number) like lower(concat('%', cast(:search as text), '%'))
                  or lower(c.customer_name) like lower(concat('%', cast(:search as text), '%'))
                  or c.mobile_no like concat('%', cast(:search as text), '%'))
             and (cast(:fromDate as date) is null or q.quotation_date >= cast(:fromDate as date))
             and (cast(:toDate as date) is null or q.quotation_date <= cast(:toDate as date))
           group by 1, 2
           """, nativeQuery = true)
    List<StatsRow> stats(@Param("tenantId") Long tenantId,
                         @Param("search") String search,
                         @Param("fromDate") LocalDate fromDate,
                         @Param("toDate") LocalDate toDate,
                         @Param("today") LocalDate today);


    long countByTenantIdAndCustomerId(Long tenantId, Long customerId);

    /** Customer 360 - a customer's quotation history (CR-030). */
    @Query("""
           select q from Quotation q
           where q.tenant.id = :tenantId and q.customer.id = :customerId
           order by q.quotationDate desc, q.id desc
           """)
    Page<Quotation> findByCustomer(@Param("tenantId") Long tenantId, @Param("customerId") Long customerId, Pageable pageable);
}
