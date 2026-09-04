package com.hardware.erp.platformadmin.service;

import com.hardware.erp.auth.entity.RevokedReason;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.platformadmin.dto.PlatformAdminActiveSessionResponse;
import com.hardware.erp.platformadmin.dto.PlatformAuditLogResponse;
import com.hardware.erp.platformadmin.dto.PlatformSecurityDashboardResponse;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAdminRefreshToken;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.repository.PlatformAdminRefreshTokenRepository;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.platformadmin.repository.PlatformAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phase 6 - Security Center. Sessions are self-service (an admin manages
 * their own, matching spec 6.3's own framing) - reuses
 * PlatformAdminRefreshToken (already tracked since CR-054 phase 1, not a
 * new table). "current" session is a documented heuristic (most recently
 * used), not a real thread-through of the caller's own token identity -
 * see PlatformAdminActiveSessionResponse's own javadoc.
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminSecurityService {

    private static final List<PlatformAuditAction> PRIVILEGED_ACTIONS = List.of(
            PlatformAuditAction.PLATFORM_ADMIN_CREATED, PlatformAuditAction.TENANT_SUSPENDED,
            PlatformAuditAction.TENANT_REACTIVATED, PlatformAuditAction.INCIDENT_RESOLVED,
            PlatformAuditAction.SUPPORT_STATUS_CHANGED, PlatformAuditAction.JOB_RETRIED,
            PlatformAuditAction.REFRESH_TOKEN_REUSE_DETECTED, PlatformAuditAction.ACCOUNT_LOCKED);

    private final PlatformAdminRefreshTokenRepository refreshTokenRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final PlatformAuditLogRepository auditLogRepository;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public PlatformSecurityDashboardResponse dashboard() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        long failedLogins = auditLogRepository.countByActionAndCreatedAtAfter(PlatformAuditAction.LOGIN_FAILURE, startOfToday);
        long mfaFailures = auditLogRepository.countByActionAndCreatedAtAfter(PlatformAuditAction.MFA_CHALLENGE_FAILED, startOfToday);
        long lockouts = auditLogRepository.countByActionAndCreatedAtAfter(PlatformAuditAction.ACCOUNT_LOCKED, startOfToday);
        long totalAdmins = platformAdminRepository.count();
        long withMfa = platformAdminRepository.countByMfaEnabled(true);
        long activeSessions = refreshTokenRepository.countActive(LocalDateTime.now());

        List<com.hardware.erp.platformadmin.entity.PlatformAuditLog> recentEntries =
                auditLogRepository.findRecentByActionIn(PRIVILEGED_ACTIONS, PageRequest.of(0, 15));
        Map<Long, String> adminEmails = resolveAdminEmails(recentEntries);
        List<PlatformAuditLogResponse> recent = recentEntries.stream()
                .map(entry -> toAuditResponse(entry, adminEmails))
                .toList();

        return new PlatformSecurityDashboardResponse(
                failedLogins, mfaFailures, lockouts, totalAdmins, withMfa, activeSessions, recent);
    }

    @Transactional(readOnly = true)
    public List<PlatformAdminActiveSessionResponse> mySessions(Long adminId) {
        List<PlatformAdminRefreshToken> active = refreshTokenRepository.findActiveByAdminId(adminId);
        return active.stream()
                .map(token -> new PlatformAdminActiveSessionResponse(
                        token.getId(), token.getIpAddress(), token.getUserAgent(),
                        token.getCreatedAt(), token.getLastUsedAt(), token.getExpiresAt(),
                        !active.isEmpty() && active.get(0).getId().equals(token.getId())))
                .toList();
    }

    @Transactional
    public void revokeSession(Long sessionId, Long adminId, HttpServletRequest request) {
        PlatformAdminRefreshToken token = refreshTokenRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
        if (!token.getAdmin().getId().equals(adminId)) {
            // Never reveal whether the session id belongs to someone else - same shape as any other id, just not found for this caller.
            throw new ResourceNotFoundException("Session", sessionId);
        }
        if (token.isRevoked()) {
            throw new BusinessException("This session is already signed out.");
        }
        token.revoke(RevokedReason.SESSION_REVOKED);
        refreshTokenRepository.save(token);

        PlatformAdmin admin = platformAdminRepository.getReferenceById(adminId);
        auditService.record(PlatformAuditAction.LOGOUT, admin, true, "SESSION", sessionId, "Session revoked by owner", request);
    }

    @Transactional
    public int revokeAllOtherSessions(Long adminId, HttpServletRequest request) {
        List<PlatformAdminRefreshToken> active = refreshTokenRepository.findActiveByAdminId(adminId);
        if (active.size() <= 1) {
            return 0;
        }
        int revoked = 0;
        for (int i = 1; i < active.size(); i++) {
            active.get(i).revoke(RevokedReason.SESSION_REVOKED);
            refreshTokenRepository.save(active.get(i));
            revoked++;
        }
        PlatformAdmin admin = platformAdminRepository.getReferenceById(adminId);
        auditService.record(PlatformAuditAction.LOGOUT_ALL, admin, true, "SESSION", null,
                revoked + " other session(s) revoked", request);
        return revoked;
    }

    private Map<Long, String> resolveAdminEmails(List<com.hardware.erp.platformadmin.entity.PlatformAuditLog> entries) {
        List<Long> ids = entries.stream().map(com.hardware.erp.platformadmin.entity.PlatformAuditLog::getAdminId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        return platformAdminRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(PlatformAdmin::getId, PlatformAdmin::getEmail));
    }

    private PlatformAuditLogResponse toAuditResponse(com.hardware.erp.platformadmin.entity.PlatformAuditLog entry,
                                                       Map<Long, String> adminEmails) {
        return new PlatformAuditLogResponse(
                entry.getId(), entry.getAdminId(),
                entry.getAdminId() == null ? null : adminEmails.get(entry.getAdminId()),
                entry.getAction(), entry.isSuccess(), entry.getTargetType(), entry.getTargetId(),
                entry.getDetail(), entry.getIpAddress(), entry.getUserAgent(), entry.getCreatedAt());
    }
}
