package com.hardware.erp.platformadmin.service;

import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.platformadmin.dto.PlatformIncidentResponse;
import com.hardware.erp.platformadmin.entity.*;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.platformadmin.repository.PlatformIncidentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformIncidentServiceTest {

    @Mock private PlatformIncidentRepository repository;
    @Mock private PlatformAdminRepository platformAdminRepository;
    @Mock private PlatformAuditService auditService;

    private PlatformIncidentService service;

    @BeforeEach
    void setUp() {
        service = new PlatformIncidentService(repository, platformAdminRepository, auditService);
        when(platformAdminRepository.getReferenceById(any())).thenReturn(
                PlatformAdmin.builder().id(1L).fullName("Admin").email("a@platform.test")
                        .role(PlatformAdminRole.SUPER_ADMIN).build());
    }

    @Test
    @DisplayName("recordFailure opens a new incident when none is active for that service")
    void recordFailureOpensNewIncident() {
        when(repository.findFirstByServiceAndStatusIn(eq(PlatformService.DATABASE), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> {
            PlatformIncident i = inv.getArgument(0);
            i.setId(42L);
            return i;
        });

        service.recordFailure(PlatformService.DATABASE, IncidentSeverity.HIGH, "Database is DOWN", "connection refused");

        var captor = org.mockito.ArgumentCaptor.forClass(PlatformIncident.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getOccurrenceCount()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo(PlatformIncidentStatus.OPEN);
        verify(auditService).record(eq(PlatformAuditAction.INCIDENT_OPENED), isNull(), eq(true),
                eq("INCIDENT"), eq(42L), any(), isNull());
    }

    @Test
    @DisplayName("recordFailure bumps occurrenceCount/lastSeen on an already-active incident instead of duplicating")
    void recordFailureBumpsExisting() {
        PlatformIncident existing = PlatformIncident.builder().id(7L).service(PlatformService.WHATSAPP)
                .severity(IncidentSeverity.MEDIUM).title("old").status(PlatformIncidentStatus.OPEN)
                .firstSeen(LocalDateTime.now().minusHours(2)).lastSeen(LocalDateTime.now().minusHours(1))
                .occurrenceCount(3).build();
        when(repository.findFirstByServiceAndStatusIn(eq(PlatformService.WHATSAPP), any())).thenReturn(Optional.of(existing));

        service.recordFailure(PlatformService.WHATSAPP, IncidentSeverity.MEDIUM, "WhatsApp is DEGRADED", "1 of 2 need attention");

        assertThat(existing.getOccurrenceCount()).isEqualTo(4);
        verify(repository).save(existing);
        verify(auditService, never()).record(eq(PlatformAuditAction.INCIDENT_OPENED), any(), anyBoolean(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("autoResolveIfOpen closes an active incident and leaves a resolved-by-system trail")
    void autoResolveClosesIncident() {
        PlatformIncident existing = PlatformIncident.builder().id(8L).service(PlatformService.EMAIL)
                .severity(IncidentSeverity.LOW).title("t").status(PlatformIncidentStatus.INVESTIGATING)
                .firstSeen(LocalDateTime.now()).lastSeen(LocalDateTime.now()).occurrenceCount(1).build();
        when(repository.findFirstByServiceAndStatusIn(eq(PlatformService.EMAIL), any())).thenReturn(Optional.of(existing));

        service.autoResolveIfOpen(PlatformService.EMAIL);

        assertThat(existing.getStatus()).isEqualTo(PlatformIncidentStatus.RESOLVED);
        assertThat(existing.getResolvedAt()).isNotNull();
        assertThat(existing.getResolvedBy()).isNull();
        verify(auditService).record(eq(PlatformAuditAction.INCIDENT_AUTO_RESOLVED), isNull(), eq(true),
                any(), any(), any(), isNull());
    }

    @Test
    @DisplayName("autoResolveIfOpen is a no-op when nothing is active for that service")
    void autoResolveNoOpWhenNothingActive() {
        when(repository.findFirstByServiceAndStatusIn(eq(PlatformService.BACKEND), any())).thenReturn(Optional.empty());

        service.autoResolveIfOpen(PlatformService.BACKEND);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("resolve() by an admin records the acting admin as resolvedBy")
    void resolveByAdminRecordsResolver() {
        PlatformIncident incident = PlatformIncident.builder().id(9L).service(PlatformService.DATABASE)
                .severity(IncidentSeverity.HIGH).title("t").status(PlatformIncidentStatus.OPEN)
                .firstSeen(LocalDateTime.now()).lastSeen(LocalDateTime.now()).occurrenceCount(1).build();
        when(repository.findById(9L)).thenReturn(Optional.of(incident));

        PlatformIncidentResponse response = service.resolve(9L, 1L, mock(HttpServletRequest.class));

        assertThat(response.status()).isEqualTo(PlatformIncidentStatus.RESOLVED);
        assertThat(incident.getResolvedBy()).isEqualTo(1L);
    }

    @Test
    @DisplayName("resolving an already-resolved incident is refused, not a silent no-op")
    void resolveAlreadyResolvedThrows() {
        PlatformIncident incident = PlatformIncident.builder().id(10L).service(PlatformService.DATABASE)
                .severity(IncidentSeverity.HIGH).title("t").status(PlatformIncidentStatus.RESOLVED)
                .firstSeen(LocalDateTime.now()).lastSeen(LocalDateTime.now()).occurrenceCount(1).build();
        when(repository.findById(10L)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> service.resolve(10L, 1L, mock(HttpServletRequest.class)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("markInvestigating refuses a non-OPEN incident")
    void markInvestigatingRefusesNonOpen() {
        PlatformIncident incident = PlatformIncident.builder().id(11L).service(PlatformService.DATABASE)
                .severity(IncidentSeverity.HIGH).title("t").status(PlatformIncidentStatus.RESOLVED)
                .firstSeen(LocalDateTime.now()).lastSeen(LocalDateTime.now()).occurrenceCount(1).build();
        when(repository.findById(11L)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> service.markInvestigating(11L, 1L, mock(HttpServletRequest.class)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("reopen refuses an already-active incident")
    void reopenRefusesAlreadyActive() {
        PlatformIncident incident = PlatformIncident.builder().id(12L).service(PlatformService.DATABASE)
                .severity(IncidentSeverity.HIGH).title("t").status(PlatformIncidentStatus.OPEN)
                .firstSeen(LocalDateTime.now()).lastSeen(LocalDateTime.now()).occurrenceCount(1).build();
        when(repository.findById(12L)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> service.reopen(12L, 1L, mock(HttpServletRequest.class)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("reopen on a resolved incident clears resolvedAt/resolvedBy and reopens it")
    void reopenClearsResolution() {
        PlatformIncident incident = PlatformIncident.builder().id(13L).service(PlatformService.DATABASE)
                .severity(IncidentSeverity.HIGH).title("t").status(PlatformIncidentStatus.RESOLVED)
                .firstSeen(LocalDateTime.now()).lastSeen(LocalDateTime.now()).occurrenceCount(1)
                .resolvedAt(LocalDateTime.now()).resolvedBy(1L).build();
        when(repository.findById(13L)).thenReturn(Optional.of(incident));

        PlatformIncidentResponse response = service.reopen(13L, 1L, mock(HttpServletRequest.class));

        assertThat(response.status()).isEqualTo(PlatformIncidentStatus.OPEN);
        assertThat(incident.getResolvedAt()).isNull();
        assertThat(incident.getResolvedBy()).isNull();
    }

    @Test
    @DisplayName("an unknown incident id is a real not-found")
    void unknownIncidentThrowsNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(999L, 1L, mock(HttpServletRequest.class)))
                .isInstanceOf(com.hardware.erp.common.exception.ResourceNotFoundException.class);
    }
}
