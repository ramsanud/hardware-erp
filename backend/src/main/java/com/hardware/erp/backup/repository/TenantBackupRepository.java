package com.hardware.erp.backup.repository;

import com.hardware.erp.backup.entity.TenantBackup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantBackupRepository extends JpaRepository<TenantBackup, Long> {

    List<TenantBackup> findTop20ByTenantIdOrderByCreatedAtDesc(Long tenantId);

    Optional<TenantBackup> findByIdAndTenantId(Long id, Long tenantId);

    /** Keeps the newest {@code keep} rows for a tenant; everything older goes, snapshot bytes included. */
    @Modifying
    @Query(value = """
            DELETE FROM tenant_backup
            WHERE tenant_id = :tenantId
              AND tenant_backup_id NOT IN (
                  SELECT tenant_backup_id FROM tenant_backup
                  WHERE tenant_id = :tenantId ORDER BY created_at DESC LIMIT :keep)
            """, nativeQuery = true)
    int pruneOlderThanNewest(@Param("tenantId") Long tenantId, @Param("keep") int keep);
}
