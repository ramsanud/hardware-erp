package com.hardware.erp.deliverychallan.repository;

import com.hardware.erp.deliverychallan.entity.DeliveryChallan;
import com.hardware.erp.deliverychallan.entity.DeliveryChallanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DeliveryChallanRepository extends JpaRepository<DeliveryChallan, Long> {

    Optional<DeliveryChallan> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
           select dc from DeliveryChallan dc
           where dc.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(dc.deliveryChallanNumber) like lower(concat('%', cast(:search as string), '%'))
                  or lower(dc.customer.customerName) like lower(concat('%', cast(:search as string), '%'))
                  or dc.customer.mobileNo like concat('%', cast(:search as string), '%'))
             and (:status is null or dc.status = :status)
             and (:fromDate is null or dc.challanDate >= :fromDate)
             and (:toDate is null or dc.challanDate <= :toDate)
           order by dc.challanDate desc, dc.id desc
           """)
    Page<DeliveryChallan> search(@Param("tenantId") Long tenantId,
                                  @Param("search") String search,
                                  @Param("status") DeliveryChallanStatus status,
                                  @Param("fromDate") LocalDate fromDate,
                                  @Param("toDate") LocalDate toDate,
                                  Pageable pageable);

    long countByTenantIdAndCustomerId(Long tenantId, Long customerId);
}
