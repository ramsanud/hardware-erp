package com.hardware.erp.purchase.repository;

import com.hardware.erp.purchase.entity.Purchase;
import com.hardware.erp.purchase.entity.PurchaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Optional<Purchase> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
           select p from Purchase p
           where p.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(p.purchaseNumber) like lower(concat('%', cast(:search as string), '%'))
                  or lower(p.supplierBillNumber) like lower(concat('%', cast(:search as string), '%'))
                  or lower(p.supplier.supplierName) like lower(concat('%', cast(:search as string), '%')))
             and (:status is null or p.status = :status)
           order by p.purchaseDate desc, p.id desc
           """)
    Page<Purchase> search(@Param("tenantId") Long tenantId,
                          @Param("search") String search,
                          @Param("status") PurchaseStatus status,
                          Pageable pageable);


    /** Duplicate-bill detection (spec §14) - a real supplier bill number match against the same supplier/date is the strongest available signal without OCR-confidence data. */
    @Query("""
           select p from Purchase p
           where p.tenant.id = :tenantId and p.supplier.id = :supplierId
             and p.status <> 'CANCELLED'
             and ((:billNumber is not null and lower(p.supplierBillNumber) = lower(cast(:billNumber as string)))
                  or (p.purchaseDate = :purchaseDate and p.totalPaise = :totalPaise))
           order by p.id desc
           """)
    List<Purchase> findPossibleDuplicates(@Param("tenantId") Long tenantId,
                                          @Param("supplierId") Long supplierId,
                                          @Param("billNumber") String billNumber,
                                          @Param("purchaseDate") LocalDate purchaseDate,
                                          @Param("totalPaise") Long totalPaise);

    @Query("""
           select coalesce(sum(p.totalPaise), 0), coalesce(sum(p.balancePaise), 0)
           from Purchase p
           where p.tenant.id = :tenantId and p.status <> 'CANCELLED'
           """)
    List<Object[]> tenantPurchaseSummary(@Param("tenantId") Long tenantId);

    /** CR-053 backlog item 2 (Tally export). Cancelled purchases excluded - a cancelled bill never really happened. */
    @Query("""
           select p from Purchase p
           where p.tenant.id = :tenantId and p.status <> 'CANCELLED'
             and p.purchaseDate >= :fromDate and p.purchaseDate <= :toDate
           order by p.purchaseDate asc, p.id asc
           """)
    List<Purchase> findForExport(@Param("tenantId") Long tenantId,
                                 @Param("fromDate") LocalDate fromDate,
                                 @Param("toDate") LocalDate toDate);
}
