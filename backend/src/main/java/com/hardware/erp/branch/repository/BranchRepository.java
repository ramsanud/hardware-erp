package com.hardware.erp.branch.repository;

import com.hardware.erp.branch.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByTenantIdOrderByMainDescBranchNameAsc(Long tenantId);

    Optional<Branch> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Branch> findByTenantIdAndMainTrue(Long tenantId);

    boolean existsByTenantIdAndBranchCodeIgnoreCase(Long tenantId, String branchCode);

    long countByTenantId(Long tenantId);
}
