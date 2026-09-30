package com.hardware.erp.discovery.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.discovery.dto.DiscoveryDtos.OwnerNotificationResponse;
import com.hardware.erp.discovery.entity.OwnerNotificationType;
import org.springframework.data.domain.Pageable;

/** CR-090. In-app notifications for the shop's own people. */
public interface OwnerNotificationService {

    void notify(Long tenantId, OwnerNotificationType type, String title, String body,
                String referenceType, Long referenceId);

    PageResponse<OwnerNotificationResponse> search(boolean unreadOnly, Pageable pageable);

    long unreadCount();

    OwnerNotificationResponse markRead(Long id);

    int markAllRead();
}
