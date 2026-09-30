package com.hardware.erp.discovery.repository;

import com.hardware.erp.discovery.entity.OwnerNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OwnerNotificationRepository extends JpaRepository<OwnerNotification, Long> {

    Optional<OwnerNotification> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
            SELECT n FROM OwnerNotification n
            WHERE n.tenantId = :tenantId
              AND (:unreadOnly = false OR n.readAt IS NULL)
            ORDER BY n.createdAt DESC
            """)
    Page<OwnerNotification> search(Long tenantId, boolean unreadOnly, Pageable pageable);

    long countByTenantIdAndReadAtIsNull(Long tenantId);
}
