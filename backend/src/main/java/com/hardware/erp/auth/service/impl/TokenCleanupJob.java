package com.hardware.erp.auth.service.impl;

import com.hardware.erp.auth.repository.EmailOtpRepository;
import com.hardware.erp.auth.repository.PasswordResetTokenRepository;
import com.hardware.erp.auth.repository.RefreshTokenRepository;
import com.hardware.erp.auth.repository.SecurityAuditLogRepository;
import com.hardware.erp.platformadmin.service.JobExecutionTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupJob {

    public static final String JOB_NAME = "token-cleanup";

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailOtpRepository emailOtpRepository;
    private final SecurityAuditLogRepository auditLogRepository;
    private final JobExecutionTracker jobExecutionTracker;

    @Value("${app.cleanup.expired-token-grace-days:7}")
    private int graceDays;

    /**
     * Audit retention in days. Default 0 means never delete.
     *
     * Audit records are not garbage. They are deleted only when a retention
     * policy has been set deliberately, never on a built-in default.
     */
    @Value("${app.cleanup.audit-retention-days:0}")
    private int auditRetentionDays;

    @Scheduled(cron = "${app.cleanup.cron:0 0 2 * * *}", zone = "Asia/Kolkata")
    @Transactional
    public void purge() {
        Long runId = jobExecutionTracker.start(JOB_NAME);
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(graceDays);
            int refreshTokens = refreshTokenRepository.deleteExpiredBefore(cutoff);
            int resetTokens = resetTokenRepository.deleteExpiredBefore(cutoff);
            // CR-078 - a ten-minute code has no value a week later, but the row
            // still says a code was sent; the same grace period keeps it around
            // for exactly as long as a reset token.
            int emailOtps = emailOtpRepository.deleteExpiredBefore(cutoff);

            int auditRows = 0;
            if (auditRetentionDays > 0) {
                auditRows = auditLogRepository.deleteOlderThan(
                        LocalDateTime.now().minusDays(auditRetentionDays));
            }

            if (refreshTokens > 0 || resetTokens > 0 || emailOtps > 0 || auditRows > 0) {
                log.info("Cleanup removed {} refresh tokens, {} reset tokens, {} email codes, {} audit rows",
                        refreshTokens, resetTokens, emailOtps, auditRows);
            }
            jobExecutionTracker.success(runId, "%d refresh tokens, %d reset tokens, %d email codes, %d audit rows removed"
                    .formatted(refreshTokens, resetTokens, emailOtps, auditRows));
        } catch (Exception ex) {
            jobExecutionTracker.failure(runId, ex.getMessage());
            throw ex;
        }
    }
}
