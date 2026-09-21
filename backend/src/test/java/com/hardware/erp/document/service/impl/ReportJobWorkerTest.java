package com.hardware.erp.document.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.document.entity.ReportJob;
import com.hardware.erp.document.entity.ReportJobFormat;
import com.hardware.erp.document.entity.ReportJobStatus;
import com.hardware.erp.document.repository.ReportJobRepository;
import com.hardware.erp.document.service.ReportJobRenderer;
import com.hardware.erp.document.service.ReportJobRenderer.RenderedFile;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.security.AppUserDetailsService;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CR-101. The worker's own contract: PENDING -> PROCESSING -> COMPLETED or
 * FAILED, the security context is set for the requesting user's tenant and
 * always cleared afterward (even on failure), and a job whose requester can
 * no longer be authenticated ends FAILED rather than throwing out of the
 * void {@code @Async} method (see AsyncConfig's own javadoc on why that
 * matters - the exception would otherwise reach only a log line).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportJobWorkerTest {

    @Mock private ReportJobRepository repository;
    @Mock private AppUserDetailsService userDetailsService;
    @Mock private ReportJobRenderer renderer;

    private ReportJobWorker worker;

    private static Tenant tenant(long id) {
        return Tenant.builder().id(id).slug("bss").name("BSS Hardware").status(TenantStatus.ACTIVE).build();
    }

    private static AppUserDetails activeUser(Tenant tenant) {
        Role owner = Role.builder().id(1L).tenant(tenant).code("OWNER").name("Owner").build();
        User user = User.builder().id(1L).tenant(tenant).role(owner)
                .fullName("Udayakumar").mobileNo("6374005608").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        return new AppUserDetails(user);
    }

    private static ReportJob pendingJob() {
        return ReportJob.builder().id(1L).tenantId(7L).requestedBy(1L)
                .reportType("DAY_BOOK").format(ReportJobFormat.PDF)
                .paramsJson("{\"from\":\"2026-09-01\",\"to\":\"2026-09-20\"}")
                .status(ReportJobStatus.PENDING).build();
    }

    private ReportJobWorker workerWith(List<ReportJobRenderer> renderers) {
        return new ReportJobWorker(repository, userDetailsService, renderers, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a successful render leaves the row COMPLETED with the file, and clears the security context")
    void successfulRenderCompletesTheJob() {
        ReportJob job = pendingJob();
        when(repository.findById(1L)).thenReturn(Optional.of(job));
        when(userDetailsService.loadById(1L)).thenReturn(Optional.of(activeUser(tenant(7L))));
        when(renderer.supports("DAY_BOOK")).thenReturn(true);
        when(renderer.render(eq("DAY_BOOK"), eq(ReportJobFormat.PDF), any()))
                .thenReturn(new RenderedFile("%PDF-fake".getBytes(), "day-book-2026-09-20.pdf", "application/pdf"));

        worker = workerWith(List.of(renderer));
        worker.process(1L);

        ArgumentCaptor<ReportJob> saved = ArgumentCaptor.forClass(ReportJob.class);
        verify(repository, times(2)).save(saved.capture());
        ReportJob finalState = saved.getValue();
        assertThat(finalState.getStatus()).isEqualTo(ReportJobStatus.COMPLETED);
        assertThat(finalState.getFileName()).isEqualTo("day-book-2026-09-20.pdf");
        assertThat(finalState.getFileData()).isEqualTo("%PDF-fake".getBytes());
        assertThat(finalState.getErrorMessage()).isNull();
        assertThat(finalState.getCompletedAt()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("a renderer failure leaves the row FAILED with a readable message, never stuck in PROCESSING")
    void rendererFailureFailsTheJob() {
        ReportJob job = pendingJob();
        when(repository.findById(1L)).thenReturn(Optional.of(job));
        when(userDetailsService.loadById(1L)).thenReturn(Optional.of(activeUser(tenant(7L))));
        when(renderer.supports("DAY_BOOK")).thenReturn(true);
        when(renderer.render(eq("DAY_BOOK"), eq(ReportJobFormat.PDF), any()))
                .thenThrow(new RuntimeException("Both from and to dates are required"));

        worker = workerWith(List.of(renderer));
        worker.process(1L);

        ArgumentCaptor<ReportJob> saved = ArgumentCaptor.forClass(ReportJob.class);
        verify(repository, times(2)).save(saved.capture());
        ReportJob finalState = saved.getValue();
        assertThat(finalState.getStatus()).isEqualTo(ReportJobStatus.FAILED);
        assertThat(finalState.getErrorMessage()).isEqualTo("Both from and to dates are required");
        assertThat(finalState.getFileData()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("a requester who no longer exists or is inactive fails the job instead of authenticating as no one")
    void missingRequesterFailsTheJob() {
        ReportJob job = pendingJob();
        when(repository.findById(1L)).thenReturn(Optional.of(job));
        when(userDetailsService.loadById(1L)).thenReturn(Optional.empty());

        worker = workerWith(List.of(renderer));
        worker.process(1L);

        ArgumentCaptor<ReportJob> saved = ArgumentCaptor.forClass(ReportJob.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ReportJobStatus.FAILED);
        assertThat(saved.getValue().getErrorMessage()).contains("no longer active");
    }

    @Test
    @DisplayName("an unknown report type fails the job with a clear message rather than a stack trace only in the log")
    void unknownReportTypeFailsTheJob() {
        ReportJob job = pendingJob();
        job.setReportType("SOMETHING_MADE_UP");
        when(repository.findById(1L)).thenReturn(Optional.of(job));
        when(userDetailsService.loadById(1L)).thenReturn(Optional.of(activeUser(tenant(7L))));
        when(renderer.supports("SOMETHING_MADE_UP")).thenReturn(false);

        worker = workerWith(List.of(renderer));
        worker.process(1L);

        ArgumentCaptor<ReportJob> saved = ArgumentCaptor.forClass(ReportJob.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ReportJobStatus.FAILED);
        assertThat(saved.getValue().getErrorMessage()).contains("Unknown report type");
    }

    @Test
    @DisplayName("a job that vanished before processing (deleted by cleanup) is a no-op, not an exception")
    void vanishedJobIsANoOp() {
        when(repository.findById(404L)).thenReturn(Optional.empty());
        worker = workerWith(List.of(renderer));

        worker.process(404L);

        verify(repository, times(0)).save(any());
    }
}
