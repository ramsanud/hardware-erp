package com.hardware.erp.auth.service.impl;

import com.hardware.erp.auth.entity.AuditAction;
import com.hardware.erp.auth.entity.SecurityAuditLog;
import com.hardware.erp.auth.repository.SecurityAuditLogRepository;
import com.hardware.erp.auth.service.SecurityAuditService;
import com.hardware.erp.common.web.RequestCorrelationFilter;
import com.hardware.erp.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditServiceImpl implements SecurityAuditService {

    private static final int MAX_REASON = 255;

    private final SecurityAuditLogRepository auditLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(AuditAction action, Long userId, String fullName,
                        String entityType, Long entityId) {
        write(action, userId, fullName, entityType, entityId, true, null);
    }

    /**
     * REQUIRES_NEW so a failed login is still recorded even though the calling
     * transaction rolls back. Without it the audit row would vanish with the
     * exception, and failed logins are exactly what needs recording.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(AuditAction action, Long userId, String fullName, String failureReason) {
        write(action, userId, fullName, "USER", userId, false, failureReason);
    }

    private void write(AuditAction action, Long userId, String fullName,
                       String entityType, Long entityId,
                       boolean success, String failureReason) {
        HttpServletRequest request = currentRequest();
        try {
            auditLogRepository.save(SecurityAuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .userId(userId != null ? userId
                            : SecurityUtils.currentUserId().orElse(null))
                    .fullName(fullName)
                    .success(success)
                    .failureReason(truncate(failureReason))
                    .ipAddress(request != null ? SecurityUtils.clientIp(request) : null)
                    .userAgent(request != null ? SecurityUtils.userAgent(request) : null)
                    .requestId(request != null
                            ? RequestCorrelationFilter.currentRequestId(request) : null)
                    .build());
        } catch (DataAccessException ex) {
            // An audit write must never break the user's action. It must also
            // never disappear silently: this is logged at ERROR so a broken
            // audit trail surfaces in monitoring rather than at the next audit.
            log.error("AUDIT WRITE FAILED action={} userId={} - security events are "
                      + "not being persisted", action, userId, ex);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_REASON ? value : value.substring(0, MAX_REASON);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
