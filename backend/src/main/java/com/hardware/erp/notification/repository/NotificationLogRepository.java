package com.hardware.erp.notification.repository;

import com.hardware.erp.notification.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Page<NotificationLog> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);
}
