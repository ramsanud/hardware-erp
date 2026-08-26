package com.hardware.erp.labour.repository;

import com.hardware.erp.labour.entity.WorkerPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkerPaymentRepository extends JpaRepository<WorkerPayment, Long> {

    Optional<WorkerPayment> findByIdAndTenantId(Long id, Long tenantId);

    List<WorkerPayment> findByTenantIdAndWorkerIdOrderByPaymentDateDesc(Long tenantId, Long workerId);

    // A CANCELLED payment stays in the history list but must never count
    // towards what the worker has actually been paid (CR-037).
    @Query("""
           select coalesce(sum(p.amountPaise), 0) from WorkerPayment p
           where p.tenant.id = :tenantId and p.worker.id = :workerId
             and p.status = com.hardware.erp.labour.entity.WorkerPaymentStatus.ACTIVE
             and (cast(:fromDate as date) is null or p.paymentDate >= :fromDate)
             and (cast(:toDate as date) is null or p.paymentDate <= :toDate)
           """)
    long sumAmountByWorker(@Param("tenantId") Long tenantId, @Param("workerId") Long workerId,
                            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
