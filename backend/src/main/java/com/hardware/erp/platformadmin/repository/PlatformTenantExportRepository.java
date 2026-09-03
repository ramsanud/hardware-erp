package com.hardware.erp.platformadmin.repository;

import com.hardware.erp.platformadmin.entity.PlatformTenantExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformTenantExportRepository extends JpaRepository<PlatformTenantExport, Long> {

    List<PlatformTenantExport> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
