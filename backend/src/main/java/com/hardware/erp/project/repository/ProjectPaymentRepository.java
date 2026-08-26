package com.hardware.erp.project.repository;

import com.hardware.erp.project.entity.ProjectPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectPaymentRepository extends JpaRepository<ProjectPayment, Long> {

    List<ProjectPayment> findByProjectIdAndTenantIdOrderByPaymentDateDesc(Long projectId, Long tenantId);

    @Query("select coalesce(sum(p.amountPaise), 0) from ProjectPayment p where p.project.id = :projectId and p.tenant.id = :tenantId")
    long sumAmountByProject(@Param("projectId") Long projectId, @Param("tenantId") Long tenantId);
}
