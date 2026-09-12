package com.hardware.erp.common.activity;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    Page<ActivityLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, Long entityId, Pageable pageable);

    /**
     * CR-053 backlog item 6 (per-user activity feed). activity_log carries
     * no tenant_id of its own (a pre-existing gap, not introduced here -
     * see SECURITY_REGISTRY.md) - safe here only because the caller
     * verifies userId belongs to its own tenant before ever reaching this
     * query (see UserActivityService). Never expose this repository method
     * behind an endpoint that takes an unverified userId directly.
     */
    Page<ActivityLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * CR-072. The tenant-scoped read behind GET /v1/activity-log.
     *
     * tenantId is MANDATORY and comes from the JWT. It is a separate method
     * from search(...) below rather than an extra nullable parameter on it,
     * precisely so no caller can reach the shop-wide shape by passing null:
     * a nullable tenant on a shared query is one careless call away from
     * serving every tenant at once.
     *
     * Rows with a null tenant_id - written by a scheduled job or an import,
     * see V55 - match nobody here, because NULL never equals anything in SQL.
     * That is deliberate, not an oversight.
     */
    @Query("""
           select a from ActivityLog a
           where a.tenantId = :tenantId
             and (:moduleCode is null or a.moduleCode = :moduleCode)
             and (:entityType is null or a.entityType = :entityType)
             and (:entityId is null or a.entityId = :entityId)
             and (:userId is null or a.userId = :userId)
             and (cast(:fromDate as timestamp) is null or a.createdAt >= :fromDate)
             and (cast(:toDate as timestamp) is null or a.createdAt <= :toDate)
           order by a.createdAt desc
           """)
    Page<ActivityLog> searchForTenant(@Param("tenantId") Long tenantId,
                                      @Param("moduleCode") String moduleCode,
                                      @Param("entityType") String entityType,
                                      @Param("entityId") Long entityId,
                                      @Param("userId") Long userId,
                                      @Param("fromDate") LocalDateTime fromDate,
                                      @Param("toDate") LocalDateTime toDate,
                                      Pageable pageable);

    /** CR-072. The distinct module codes this shop actually has history for, so the filter offers only what will return something. */
    @Query("select distinct a.moduleCode from ActivityLog a where a.tenantId = :tenantId order by a.moduleCode")
    java.util.List<String> distinctModuleCodesForTenant(@Param("tenantId") Long tenantId);

    @Query("""
           select a from ActivityLog a
           where (:moduleCode is null or a.moduleCode = :moduleCode)
             and (:entityType is null or a.entityType = :entityType)
             and (:entityId is null or a.entityId = :entityId)
             and (:userId is null or a.userId = :userId)
           """)
    Page<ActivityLog> search(@Param("moduleCode") String moduleCode,
                             @Param("entityType") String entityType,
                             @Param("entityId") Long entityId,
                             @Param("userId") Long userId,
                             Pageable pageable);
}
