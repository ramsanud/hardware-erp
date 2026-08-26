package com.hardware.erp.project.repository;

import com.hardware.erp.project.entity.WorkType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkTypeRepository extends JpaRepository<WorkType, Long> {

    List<WorkType> findByTenantIdOrderByNameAsc(Long tenantId);

    Optional<WorkType> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByNameIgnoreCaseAndTenantId(String name, Long tenantId);

    boolean existsByNameIgnoreCaseAndTenantIdAndIdNot(String name, Long tenantId, Long id);
}
