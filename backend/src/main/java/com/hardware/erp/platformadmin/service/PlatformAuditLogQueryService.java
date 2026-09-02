package com.hardware.erp.platformadmin.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.platformadmin.dto.PlatformAuditLogResponse;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.entity.PlatformAuditLog;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.platformadmin.repository.PlatformAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global Audit Log viewer (CR-057 phase 6). Read-only over the append-only
 * platform_audit_log table - never a mutation. adminId is deliberately not
 * a FK (see V39/CR-054), so admin email is resolved here with a single
 * batched lookup per page rather than a join, and is null when that admin
 * account no longer exists - the log entry itself must still be readable.
 */
@Service
@RequiredArgsConstructor
public class PlatformAuditLogQueryService {

    private final PlatformAuditLogRepository auditLogRepository;
    private final PlatformAdminRepository platformAdminRepository;

    @Transactional(readOnly = true)
    public PageResponse<PlatformAuditLogResponse> search(Long adminId, PlatformAuditAction action, Boolean success,
                                                           String targetType, LocalDateTime fromDate,
                                                           LocalDateTime toDate, Pageable pageable) {
        Page<PlatformAuditLog> page = auditLogRepository.search(adminId, action, success, targetType, fromDate, toDate, pageable);

        List<Long> adminIds = page.getContent().stream()
                .map(PlatformAuditLog::getAdminId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> adminEmails = platformAdminRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(PlatformAdmin::getId, PlatformAdmin::getEmail));

        return PageResponse.from(page, entry -> toResponse(entry, adminEmails));
    }

    private PlatformAuditLogResponse toResponse(PlatformAuditLog entry, Map<Long, String> adminEmails) {
        return new PlatformAuditLogResponse(
                entry.getId(), entry.getAdminId(),
                entry.getAdminId() == null ? null : adminEmails.get(entry.getAdminId()),
                entry.getAction(), entry.isSuccess(), entry.getTargetType(), entry.getTargetId(),
                entry.getDetail(), entry.getIpAddress(), entry.getUserAgent(), entry.getCreatedAt());
    }
}
