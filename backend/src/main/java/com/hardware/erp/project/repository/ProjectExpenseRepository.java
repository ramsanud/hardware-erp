package com.hardware.erp.project.repository;

import com.hardware.erp.project.entity.ProjectExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectExpenseRepository extends JpaRepository<ProjectExpense, Long> {

    List<ProjectExpense> findByProjectIdAndTenantIdOrderByExpenseDateDesc(Long projectId, Long tenantId);

    Optional<ProjectExpense> findByIdAndProjectIdAndTenantId(Long id, Long projectId, Long tenantId);

    @Query("select coalesce(sum(e.amountPaise), 0) from ProjectExpense e where e.project.id = :projectId and e.tenant.id = :tenantId")
    long sumAmountByProject(@Param("projectId") Long projectId, @Param("tenantId") Long tenantId);
}
