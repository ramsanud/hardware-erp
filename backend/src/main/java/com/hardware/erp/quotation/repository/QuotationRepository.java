package com.hardware.erp.quotation.repository;

import com.hardware.erp.quotation.entity.Quotation;
import com.hardware.erp.quotation.entity.QuotationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    Optional<Quotation> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
           select q from Quotation q
           where q.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(q.quotationNumber) like lower(concat('%', cast(:search as string), '%'))
                  or lower(q.customer.customerName) like lower(concat('%', cast(:search as string), '%'))
                  or q.customer.mobileNo like concat('%', cast(:search as string), '%'))
             and (:status is null or q.status = :status)
             and (:fromDate is null or q.quotationDate >= :fromDate)
             and (:toDate is null or q.quotationDate <= :toDate)
           order by q.quotationDate desc, q.id desc
           """)
    Page<Quotation> search(@Param("tenantId") Long tenantId,
                            @Param("search") String search,
                            @Param("status") QuotationStatus status,
                            @Param("fromDate") LocalDate fromDate,
                            @Param("toDate") LocalDate toDate,
                            Pageable pageable);


    long countByTenantIdAndCustomerId(Long tenantId, Long customerId);

    /** Customer 360 - a customer's quotation history (CR-030). */
    @Query("""
           select q from Quotation q
           where q.tenant.id = :tenantId and q.customer.id = :customerId
           order by q.quotationDate desc, q.id desc
           """)
    Page<Quotation> findByCustomer(@Param("tenantId") Long tenantId, @Param("customerId") Long customerId, Pageable pageable);
}
