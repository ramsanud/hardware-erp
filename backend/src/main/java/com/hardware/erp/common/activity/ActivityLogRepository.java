package com.hardware.erp.common.activity;

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
