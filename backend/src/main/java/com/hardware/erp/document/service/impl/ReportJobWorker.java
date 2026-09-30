package com.hardware.erp.document.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.document.entity.ReportJob;
import com.hardware.erp.document.entity.ReportJobStatus;
import com.hardware.erp.document.repository.ReportJobRepository;
import com.hardware.erp.document.service.ReportJobRenderer;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.security.AppUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * CR-101. The background half of the job queue - a separate bean from
 * {@code ReportJobServiceImpl} on purpose. {@code @Async} is AOP-proxied
 * exactly like {@code @Transactional}, so a method calling its own
 * {@code @Async} method never actually goes async (BUG-BE-002's lesson,
 * restated for a different annotation); crossing a real bean boundary is
 * what makes the proxy apply.
 *
 * An {@code @Async} thread starts with no transaction and no security
 * context (see AsyncConfig's own javadoc). Every {@link com.hardware.erp.report.service.ReportService}
 * method resolves its tenant from {@code SecurityUtils}, so this worker
 * reconstructs a context for the user who queued the job - the same
 * {@link UsernamePasswordAuthenticationToken} construction
 * {@code JwtAuthenticationFilter} uses for a real request - runs the
 * render, and always clears it afterward even on failure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportJobWorker {

    private final ReportJobRepository repository;
    private final AppUserDetailsService userDetailsService;
    private final List<ReportJobRenderer> renderers;
    private final ObjectMapper objectMapper;

    @Async("taskExecutor")
    public void process(Long jobId) {
        ReportJob job = repository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Report job {} vanished before it could be processed", jobId);
            return;
        }

        job.setStatus(ReportJobStatus.PROCESSING);
        job.setStartedAt(LocalDateTime.now());
        repository.save(job);

        try {
            if (!authenticateAs(job.getRequestedBy(), job.getTenantId())) {
                throw new IllegalStateException(
                        "The user who requested this export is no longer active, or has been deleted.");
            }
            ReportJobRenderer renderer = renderers.stream()
                    .filter(r -> r.supports(job.getReportType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unknown report type: " + job.getReportType()));

            ReportJobRenderer.RenderedFile file = renderer.render(job.getReportType(), job.getFormat(), readParams(job));

            job.setStatus(ReportJobStatus.COMPLETED);
            job.setFileName(file.fileName().length() <= 150 ? file.fileName() : file.fileName().substring(0, 150));
            job.setFileData(file.bytes());
            job.setFileSizeBytes(file.bytes().length);
            job.setErrorMessage(null);
        } catch (Exception ex) {
            // Same rule as LowStockSnapshotJob: one job's failure is recorded
            // on its own row, never thrown out of this method - a void @Async
            // exception only reaches AsyncConfig's log handler, which cannot
            // tell the caller anything the polled status endpoint should.
            log.warn("Report job {} ({} {}) failed", jobId, job.getReportType(), job.getFormat(), ex);
            job.setStatus(ReportJobStatus.FAILED);
            job.setErrorMessage(safeMessage(ex));
        } finally {
            job.setCompletedAt(LocalDateTime.now());
            repository.save(job);
            SecurityContextHolder.clearContext();
        }
    }

    private boolean authenticateAs(Long userId, Long expectedTenantId) {
        if (userId == null) return false;
        Optional<AppUserDetails> maybeUser = userDetailsService.loadById(userId);
        if (maybeUser.isEmpty()) return false;
        AppUserDetails principal = maybeUser.get();
        if (!principal.isEnabled() || !principal.isAccountNonLocked()) return false;
        // Defense in depth: requestedBy is always the enqueuing user's own id
        // (set from SecurityUtils in ReportJobServiceImpl), so this can only
        // fail if a user's tenant changed between enqueue and processing,
        // which never happens today - a user is never moved between tenants.
        if (!Objects.equals(principal.getTenantId(), expectedTenantId)) return false;

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }

    private Map<String, String> readParams(ReportJob job) {
        try {
            return objectMapper.readValue(job.getParamsJson(), new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** error_message is VARCHAR(500) - a stack-trace-sized message must never fail this save on top of the original failure. */
    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        String value = (message == null || message.isBlank()) ? ex.getClass().getSimpleName() : message;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
