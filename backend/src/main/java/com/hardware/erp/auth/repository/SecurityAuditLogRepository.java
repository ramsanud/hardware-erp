package com.hardware.erp.auth.repository;

import com.hardware.erp.auth.entity.AuditAction;
import com.hardware.erp.auth.entity.SecurityAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {

    /**
     * tenantId scopes to events belonging to a user of the caller's own
     * tenant (BUG-SEC-001, CR-016 was never applied here - this endpoint
     * leaked every tenant's login attempts, IPs and failure reasons to any
     * caller holding AUDIT_VIEW anywhere). A row with no resolvable user
     * (e.g. a failed login against an identifier that matches no account at
     * all) has no tenant to attribute it to and is deliberately excluded
     * rather than shown to every tenant - there is no platform-admin view
     * yet that would be the correct place for those.
     */
    @Query("""
           select a from SecurityAuditLog a
           where a.userId in (select u.id from User u where u.tenant.id = :tenantId)
             and (:userId is null or a.userId = :userId)
             and (cast(:action as string) is null or a.action = :action)
             and (cast(:from as timestamp) is null or a.createdAt >= :from)
             and (cast(:to as timestamp) is null or a.createdAt <= :to)
           """)
    Page<SecurityAuditLog> search(@Param("tenantId") Long tenantId,
                                  @Param("userId") Long userId,
                                  @Param("action") AuditAction action,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  Pageable pageable);

    /**
     * Only ever called with an explicitly configured retention period. Audit
     * records are never deleted on a default.
     */
    @Modifying
    @Query("delete from SecurityAuditLog a where a.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
