package com.hardware.erp.platformadmin.service;

import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.entity.PlatformAuditLog;
import com.hardware.erp.platformadmin.repository.PlatformAuditLogRepository;
import com.hardware.erp.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The platform-wide equivalent of SecurityAuditService, writing to
 * platform_audit_log - a table disjoint from security_audit_log (see the
 * V39 migration comment), so a bug in one audit path can never suppress or
 * corrupt evidence in the other.
 */
@Service
@RequiredArgsConstructor
public class PlatformAuditService {

    private final PlatformAuditLogRepository platformAuditLogRepository;

    /**
     * REQUIRES_NEW: an audit row must survive even when the surrounding
     * transaction (e.g. a rejected login) rolls back - mirrors
     * SecurityAuditServiceImpl's own reasoning for the tenant side.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(PlatformAuditAction action, PlatformAdmin admin, boolean success,
                       String targetType, Long targetId, String detail, HttpServletRequest request) {
        platformAuditLogRepository.save(PlatformAuditLog.builder()
                .adminId(admin == null ? null : admin.getId())
                .action(action)
                .success(success)
                .targetType(targetType)
                .targetId(targetId)
                .detail(detail)
                .ipAddress(request == null ? null : SecurityUtils.clientIp(request))
                .userAgent(request == null ? null : SecurityUtils.userAgent(request))
                .build());
    }

    public void success(PlatformAuditAction action, PlatformAdmin admin, HttpServletRequest request) {
        record(action, admin, true, null, null, null, request);
    }

    public void failure(PlatformAuditAction action, PlatformAdmin admin, String detail,
                        HttpServletRequest request) {
        record(action, admin, false, null, null, detail, request);
    }
}
