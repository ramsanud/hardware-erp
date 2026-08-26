package com.hardware.erp.common.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    Page<ActivityLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, Long entityId, Pageable pageable);

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
