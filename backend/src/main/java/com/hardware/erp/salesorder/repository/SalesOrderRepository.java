package com.hardware.erp.salesorder.repository;

import com.hardware.erp.salesorder.entity.SalesOrder;
import com.hardware.erp.salesorder.entity.SalesOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    Optional<SalesOrder> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
           select so from SalesOrder so
           where so.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(so.salesOrderNumber) like lower(concat('%', cast(:search as string), '%'))
                  or lower(so.customer.customerName) like lower(concat('%', cast(:search as string), '%'))
                  or so.customer.mobileNo like concat('%', cast(:search as string), '%'))
             and (:status is null or so.status = :status)
             and (:fromDate is null or so.orderDate >= :fromDate)
             and (:toDate is null or so.orderDate <= :toDate)
           order by so.orderDate desc, so.id desc
           """)
    Page<SalesOrder> search(@Param("tenantId") Long tenantId,
                             @Param("search") String search,
                             @Param("status") SalesOrderStatus status,
                             @Param("fromDate") LocalDate fromDate,
                             @Param("toDate") LocalDate toDate,
                             Pageable pageable);

    long countByTenantIdAndCustomerId(Long tenantId, Long customerId);
}
