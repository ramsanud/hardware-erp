package com.hardware.erp.creditnote.repository;

import com.hardware.erp.creditnote.entity.CreditNote;
import com.hardware.erp.creditnote.entity.CreditNoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {

    Optional<CreditNote> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
           select cn from CreditNote cn
           where cn.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(cn.creditNoteNumber) like lower(concat('%', cast(:search as string), '%'))
                  or lower(cn.invoice.invoiceNumber) like lower(concat('%', cast(:search as string), '%'))
                  or lower(cn.customer.customerName) like lower(concat('%', cast(:search as string), '%'))
                  or cn.customer.mobileNo like concat('%', cast(:search as string), '%'))
             and (:status is null or cn.status = :status)
             and (:fromDate is null or cn.creditNoteDate >= :fromDate)
             and (:toDate is null or cn.creditNoteDate <= :toDate)
           order by cn.creditNoteDate desc, cn.id desc
           """)
    Page<CreditNote> search(@Param("tenantId") Long tenantId,
                             @Param("search") String search,
                             @Param("status") CreditNoteStatus status,
                             @Param("fromDate") LocalDate fromDate,
                             @Param("toDate") LocalDate toDate,
                             Pageable pageable);
}
