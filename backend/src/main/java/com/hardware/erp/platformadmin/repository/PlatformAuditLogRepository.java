package com.hardware.erp.platformadmin.repository;

import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.entity.PlatformAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface PlatformAuditLogRepository extends JpaRepository<PlatformAuditLog, Long> {

    /** Global Audit Log viewer (CR-057 phase 6). Append-only table - this is a read-only search, never a mutation. */
    @Query("""
           select a from PlatformAuditLog a
           where (:adminId is null or a.adminId = :adminId)
             and (:action is null or a.action = :action)
             and (:success is null or a.success = :success)
             and (cast(:targetType as string) is null or a.targetType = :targetType)
             and (cast(:fromDate as timestamp) is null or a.createdAt >= :fromDate)
             and (cast(:toDate as timestamp) is null or a.createdAt <= :toDate)
           order by a.createdAt desc
           """)
    Page<PlatformAuditLog> search(@Param("adminId") Long adminId,
                                   @Param("action") PlatformAuditAction action,
                                   @Param("success") Boolean success,
                                   @Param("targetType") String targetType,
                                   @Param("fromDate") LocalDateTime fromDate,
                                   @Param("toDate") LocalDateTime toDate,
                                   Pageable pageable);

    /** Security Center dashboard. */
    long countByActionAndCreatedAtAfter(PlatformAuditAction action, LocalDateTime after);

    @Query("""
           select a from PlatformAuditLog a
           where a.action in :actions
           order by a.createdAt desc
           """)
    java.util.List<PlatformAuditLog> findRecentByActionIn(@Param("actions") java.util.List<PlatformAuditAction> actions,
                                                            org.springframework.data.domain.Pageable pageable);
}
