package com.hardware.erp.invoice.repository;

import com.hardware.erp.invoice.entity.Payment;
import com.hardware.erp.invoice.entity.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceIdOrderByPaymentDateAsc(Long invoiceId);

    /** Platform Admin tenant usage summary. */
    long countByTenantId(Long tenantId);

    /** Platform Admin overview KPI - "payments today" across every tenant, not scoped to one. */
    long countByPaymentDateBetween(LocalDateTime from, LocalDateTime to);

    @Query("""
           select p from Payment p
           where p.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(p.invoice.invoiceNumber) like lower(concat('%', cast(:search as string), '%'))
                  or lower(p.invoice.customer.customerName) like lower(concat('%', cast(:search as string), '%'))
                  or p.invoice.customer.mobileNo like concat('%', cast(:search as string), '%'))
             and (cast(:paymentMethod as string) is null or p.paymentMethod = :paymentMethod)
             and (cast(:fromDate as timestamp) is null or p.paymentDate >= :fromDate)
             and (cast(:toDate as timestamp) is null or p.paymentDate <= :toDate)
           order by p.paymentDate desc, p.id desc
           """)
    Page<Payment> search(@Param("tenantId") Long tenantId,
                         @Param("search") String search,
                         @Param("paymentMethod") PaymentMethod paymentMethod,
                         @Param("fromDate") LocalDateTime fromDate,
                         @Param("toDate") LocalDateTime toDate,
                         Pageable pageable);
}
