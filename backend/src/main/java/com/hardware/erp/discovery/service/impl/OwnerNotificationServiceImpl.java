package com.hardware.erp.discovery.service.impl;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.discovery.dto.DiscoveryDtos.OwnerNotificationResponse;
import com.hardware.erp.discovery.entity.OwnerNotification;
import com.hardware.erp.discovery.entity.OwnerNotificationType;
import com.hardware.erp.discovery.repository.OwnerNotificationRepository;
import com.hardware.erp.discovery.service.OwnerNotificationService;
import com.hardware.erp.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** CR-090. Tenant-scoped in-app notifications; every read resolves the tenant from the JWT. */
@Service
@RequiredArgsConstructor
public class OwnerNotificationServiceImpl implements OwnerNotificationService {

    private final OwnerNotificationRepository repository;

    @Override
    @Transactional
    public void notify(Long tenantId, OwnerNotificationType type, String title, String body,
                       String referenceType, Long referenceId) {
        repository.save(OwnerNotification.builder()
                .tenantId(tenantId)
                .notificationType(type)
                .title(title)
                .body(body.length() > 1000 ? body.substring(0, 1000) : body)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OwnerNotificationResponse> search(boolean unreadOnly, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(repository.search(tenantId, unreadOnly, pageable), this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByTenantIdAndReadAtIsNull(SecurityUtils.requireCurrentTenantId());
    }

    @Override
    @Transactional
    public OwnerNotificationResponse markRead(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        OwnerNotification notification = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            repository.save(notification);
        }
        return toResponse(notification);
    }

    @Override
    @Transactional
    public int markAllRead() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        List<OwnerNotification> unread = repository.search(tenantId, true, Pageable.unpaged()).getContent();
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(notification -> notification.setReadAt(now));
        repository.saveAll(unread);
        return unread.size();
    }

    private OwnerNotificationResponse toResponse(OwnerNotification notification) {
        return new OwnerNotificationResponse(notification.getId(), notification.getNotificationType(),
                notification.getTitle(), notification.getBody(), notification.getReferenceType(),
                notification.getReferenceId(), notification.getReadAt(), notification.getCreatedAt());
    }
}
