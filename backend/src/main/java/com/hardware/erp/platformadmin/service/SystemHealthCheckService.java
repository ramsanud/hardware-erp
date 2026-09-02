package com.hardware.erp.platformadmin.service;

import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.notification.entity.WhatsAppConnectionStatus;
import com.hardware.erp.notification.repository.TenantWhatsAppConnectionRepository;
import com.hardware.erp.platformadmin.dto.HealthStatus;
import com.hardware.erp.platformadmin.entity.JobExecutionStatus;
import com.hardware.erp.platformadmin.entity.PlatformService;
import com.hardware.erp.platformadmin.repository.JobExecutionLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

/**
 * Computes the LIVE health of every service this app can honestly check
 * from inside its own process - no external uptime-monitoring vendor, no
 * synthetic "always green". Every result here is either a real measured
 * signal or explicitly UNKNOWN, never a guess dressed up as HEALTHY.
 *
 * Architectural honesty, stated once here rather than repeated per method:
 * this app stores every file (product photos, logos, purchase documents)
 * as a bytea column in PostgreSQL, not a separate object-storage service -
 * so STORAGE health is genuinely the same signal as DATABASE health, not a
 * second independent check pretending otherwise.
 */
@Service
@RequiredArgsConstructor
public class SystemHealthCheckService {

    private static final long DEGRADED_THRESHOLD_MS = 500;

    @PersistenceContext
    private EntityManager entityManager;

    private final UserRepository userRepository;
    private final TenantWhatsAppConnectionRepository whatsAppConnectionRepository;
    private final JobExecutionLogRepository jobExecutionLogRepository;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public record CheckResult(PlatformService service, HealthStatus status, Long responseTimeMs, String detail) {}

    @Transactional(readOnly = true)
    public Map<PlatformService, CheckResult> checkAll() {
        Map<PlatformService, CheckResult> results = new EnumMap<>(PlatformService.class);
        for (PlatformService service : PlatformService.values()) {
            results.put(service, checkOne(service));
        }
        return results;
    }

    @Transactional(readOnly = true)
    public CheckResult checkOne(PlatformService service) {
        return switch (service) {
            case BACKEND -> new CheckResult(service, HealthStatus.HEALTHY, 0L,
                    "Reflects the running process handling this check - not an independent external ping.");
            case DATABASE, STORAGE -> checkDatabase(service);
            case AUTHENTICATION -> checkAuthentication();
            case WHATSAPP -> checkWhatsApp();
            case EMAIL -> checkEmail();
            case BACKGROUND_JOBS -> checkBackgroundJobs();
        };
    }

    private CheckResult checkDatabase(PlatformService service) {
        long start = System.nanoTime();
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            HealthStatus status = elapsedMs > DEGRADED_THRESHOLD_MS ? HealthStatus.DEGRADED : HealthStatus.HEALTHY;
            String detail = service == PlatformService.STORAGE
                    ? "Files are stored as bytea columns in this database - no separate object store to check."
                    : null;
            return new CheckResult(service, status, elapsedMs, detail);
        } catch (Exception ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            return new CheckResult(service, HealthStatus.DOWN, elapsedMs, ex.getMessage());
        }
    }

    private CheckResult checkAuthentication() {
        long start = System.nanoTime();
        try {
            userRepository.count();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            HealthStatus status = elapsedMs > DEGRADED_THRESHOLD_MS ? HealthStatus.DEGRADED : HealthStatus.HEALTHY;
            return new CheckResult(PlatformService.AUTHENTICATION, status, elapsedMs,
                    "Confirms the app_user table is reachable, not a full login round-trip.");
        } catch (Exception ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            return new CheckResult(PlatformService.AUTHENTICATION, HealthStatus.DOWN, elapsedMs, ex.getMessage());
        }
    }

    /**
     * No outbound call to Meta on every poll (too slow, and an unrelated
     * cost/rate-limit risk for a health check) - this reflects connection
     * status already recorded by real send attempts
     * (WhatsAppBusinessProvider marks NEEDS_ATTENTION on a real 401/403
     * from Meta), not a synthetic ping.
     */
    private CheckResult checkWhatsApp() {
        long connected = whatsAppConnectionRepository.count();
        if (connected == 0) {
            return new CheckResult(PlatformService.WHATSAPP, HealthStatus.UNKNOWN, null,
                    "No tenant has connected a WhatsApp Business account yet.");
        }
        long needsAttention = whatsAppConnectionRepository.findAll().stream()
                .filter(c -> c.getConnectionStatus() == WhatsAppConnectionStatus.NEEDS_ATTENTION)
                .count();
        if (needsAttention == 0) {
            return new CheckResult(PlatformService.WHATSAPP, HealthStatus.HEALTHY, null,
                    "%d tenant connection(s), none flagged.".formatted(connected));
        }
        HealthStatus status = needsAttention == connected ? HealthStatus.DOWN : HealthStatus.DEGRADED;
        return new CheckResult(PlatformService.WHATSAPP, status, null,
                "%d of %d tenant connection(s) need attention (Meta rejected their token on a real send)."
                        .formatted(needsAttention, connected));
    }

    private CheckResult checkEmail() {
        if (mailUsername == null || mailUsername.isBlank()) {
            return new CheckResult(PlatformService.EMAIL, HealthStatus.UNKNOWN, null,
                    "spring.mail.username is not configured - EmailNotificationProvider logs instead of sending.");
        }
        return new CheckResult(PlatformService.EMAIL, HealthStatus.HEALTHY, null,
                "SMTP username configured. This does not confirm the password/server actually accept mail - "
                        + "use Settings > Test email for that.");
    }

    private CheckResult checkBackgroundJobs() {
        String[] jobNames = { "token-cleanup", "reminder-scheduler" };
        boolean anyFailed = false;
        boolean anyRun = false;
        for (String jobName : jobNames) {
            var latest = jobExecutionLogRepository.findFirstByJobNameOrderByStartedAtDesc(jobName);
            if (latest.isPresent()) {
                anyRun = true;
                if (latest.get().getStatus() == JobExecutionStatus.FAILED) {
                    anyFailed = true;
                }
            }
        }
        if (!anyRun) {
            return new CheckResult(PlatformService.BACKGROUND_JOBS, HealthStatus.UNKNOWN, null,
                    "Neither scheduled job has run yet since this server started.");
        }
        return new CheckResult(PlatformService.BACKGROUND_JOBS,
                anyFailed ? HealthStatus.DEGRADED : HealthStatus.HEALTHY, null,
                anyFailed ? "At least one job's most recent run failed - see Developer Tools > Background Jobs."
                          : "Every job's most recent run succeeded.");
    }
}
