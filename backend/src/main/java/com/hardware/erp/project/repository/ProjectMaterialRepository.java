package com.hardware.erp.project.repository;

import com.hardware.erp.project.entity.ProjectMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectMaterialRepository extends JpaRepository<ProjectMaterial, Long> {

    List<ProjectMaterial> findByProjectIdAndTenantIdOrderByIdAsc(Long projectId, Long tenantId);

    Optional<ProjectMaterial> findByIdAndProjectIdAndTenantId(Long id, Long projectId, Long tenantId);

    @Query("select coalesce(sum(m.totalCostPaise), 0) from ProjectMaterial m where m.project.id = :projectId and m.tenant.id = :tenantId")
    long sumTotalCostByProject(@Param("projectId") Long projectId, @Param("tenantId") Long tenantId);
}
