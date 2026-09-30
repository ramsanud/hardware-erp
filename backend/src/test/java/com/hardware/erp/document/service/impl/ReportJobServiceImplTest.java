package com.hardware.erp.document.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.document.dto.ReportJobDtos.ReportJobRequest;
import com.hardware.erp.document.entity.ReportJob;
import com.hardware.erp.document.entity.ReportJobFormat;
import com.hardware.erp.document.entity.ReportJobStatus;
import com.hardware.erp.document.repository.ReportJobRepository;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CR-101. The orchestration contract: enqueue saves the row (so it is
 * durably visible) BEFORE handing its id to the worker, never the other
 * way round - see the class javadoc on ReportJobServiceImpl for why that
 * order matters with an @Async collaborator.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportJobServiceImplTest {

    @Mock private ReportJobRepository repository;
    @Mock private ReportJobWorker worker;

    private ReportJobServiceImpl service;

    @BeforeEach
    void setUp() {
        Tenant tenant = Tenant.builder().id(7L).slug("bss").name("BSS Hardware").status(TenantStatus.ACTIVE).build();
        Role owner = Role.builder().id(1L).tenant(tenant).code("OWNER").name("Owner").build();
        User user = User.builder().id(5L).tenant(tenant).role(owner)
                .fullName("Udayakumar").mobileNo("6374005608").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(user), null, List.of()));

        service = new ReportJobServiceImpl(repository, worker, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("enqueue saves a PENDING row stamped with the caller's own tenant and user, then hands its id to the worker - in that order")
    void enqueueSavesThenTriggersTheWorker() {
        when(repository.save(any(ReportJob.class))).thenAnswer(invocation -> {
            ReportJob job = invocation.getArgument(0);
            job.setId(42L);
            return job;
        });

        var response = service.enqueue(new ReportJobRequest("day_book", ReportJobFormat.PDF, Map.of("from", "2026-09-01")));

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.reportType()).isEqualTo("DAY_BOOK"); // upper-cased, matching ReportJobRenderer.supports()
        assertThat(response.status()).isEqualTo(ReportJobStatus.PENDING);

        ArgumentCaptor<ReportJob> saved = ArgumentCaptor.forClass(ReportJob.class);
        InOrder order = inOrder(repository, worker);
        order.verify(repository).save(saved.capture());
        order.verify(worker).process(42L);
        assertThat(saved.getValue().getTenantId()).isEqualTo(7L);
        assertThat(saved.getValue().getRequestedBy()).isEqualTo(5L);
        assertThat(saved.getValue().getParamsJson()).contains("2026-09-01");
    }

    @Test
    @DisplayName("download refuses a job that has not reached COMPLETED yet")
    void downloadRefusesUnfinishedJob() {
        ReportJob job = ReportJob.builder().id(1L).tenantId(7L).status(ReportJobStatus.PROCESSING).build();
        when(repository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.download(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("download refuses a job belonging to another tenant exactly as it would refuse one that never existed")
    void downloadRefusesAnotherTenantsJob() {
        when(repository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a completed job downloads with the content type its format implies")
    void downloadReturnsTheFile() {
        ReportJob job = ReportJob.builder().id(1L).tenantId(7L).status(ReportJobStatus.COMPLETED)
                .format(ReportJobFormat.XLSX).fileName("stock.xlsx").fileData(new byte[]{1, 2, 3}).build();
        when(repository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(job));

        var file = service.download(1L);

        assertThat(file.fileName()).isEqualTo("stock.xlsx");
        assertThat(file.contentType()).contains("spreadsheetml");
        assertThat(file.bytes()).containsExactly(1, 2, 3);
    }
}
