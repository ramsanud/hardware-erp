package com.hardware.erp.platformadmin.repository;

import com.hardware.erp.platformadmin.entity.PlatformAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAuditLogRepository extends JpaRepository<PlatformAuditLog, Long> {
}
