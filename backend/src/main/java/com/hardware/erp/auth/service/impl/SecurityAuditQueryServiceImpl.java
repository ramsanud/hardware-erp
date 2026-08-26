package com.hardware.erp.auth.service.impl;

import com.hardware.erp.auth.dto.SecurityAuditLogResponse;
import com.hardware.erp.auth.entity.AuditAction;
import com.hardware.erp.auth.entity.SecurityAuditLog;
import com.hardware.erp.auth.repository.SecurityAuditLogRepository;
import com.hardware.erp.auth.service.SecurityAuditQueryService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SecurityAuditQueryServiceImpl implements SecurityAuditQueryService {

    private final SecurityAuditLogRepository auditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SecurityAuditLogResponse> search(Long userId, AuditAction action,
                                                         LocalDateTime from, LocalDateTime to,
                                                         Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                auditLogRepository.search(tenantId, userId, action, from, to, pageable),
                this::toResponse);
    }

    private SecurityAuditLogResponse toResponse(SecurityAuditLog log) {
        return new SecurityAuditLogResponse(
                log.getId(), log.getAction(), log.getEntityType(), log.getEntityId(),
                log.getUserId(), log.getFullName(), log.isSuccess(), log.getFailureReason(),
                log.getIpAddress(), log.getUserAgent(), log.getRequestId(), log.getCreatedAt());
    }
}
