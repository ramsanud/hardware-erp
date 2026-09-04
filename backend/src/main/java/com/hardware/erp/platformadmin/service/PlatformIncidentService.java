package com.hardware.erp.platformadmin.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.platformadmin.dto.PlatformIncidentResponse;
import com.hardware.erp.platformadmin.entity.*;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.platformadmin.repository.PlatformIncidentRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One OPEN/INVESTIGATING incident per service at a time - recordFailure()
 * bumps occurrenceCount/lastSeen on an existing one instead of creating a
 * duplicate, matching the spec's own "First seen / Last seen / Count"
 * shape. autoResolve() closes an incident once the health check that opened
 * it starts passing again, the same way a real monitoring system would,
 * audited as a system action (no acting admin - see PlatformAuditService's
 * own null-admin support).
 */
@Service
@RequiredArgsConstructor
public class PlatformIncidentService {

    private static final List<PlatformIncidentStatus> ACTIVE_STATUSES =
            List.of(PlatformIncidentStatus.OPEN, PlatformIncidentStatus.INVESTIGATING);

    private final PlatformIncidentRepository repository;
    private final PlatformAdminRepository platformAdminRepository;
    private final PlatformAuditService auditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(PlatformService service, IncidentSeverity severity, String title, String detail) {
        var existing = repository.findFirstByServiceAndStatusIn(service, ACTIVE_STATUSES);
        LocalDateTime now = LocalDateTime.now();
        if (existing.isPresent()) {
            PlatformIncident incident = existing.get();
            incident.setLastSeen(now);
            incident.setOccurrenceCount(incident.getOccurrenceCount() + 1);
            incident.setDescription(detail);
            repository.save(incident);
            return;
        }
        PlatformIncident incident = repository.save(PlatformIncident.builder()
                .service(service)
                .severity(severity)
                .title(title)
                .description(detail)
                .status(PlatformIncidentStatus.OPEN)
                .firstSeen(now)
                .lastSeen(now)
                .occurrenceCount(1)
                .build());
        auditService.record(PlatformAuditAction.INCIDENT_OPENED, null, true,
                "INCIDENT", incident.getId(), title, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoResolveIfOpen(PlatformService service) {
        repository.findFirstByServiceAndStatusIn(service, ACTIVE_STATUSES).ifPresent(incident -> {
            incident.setStatus(PlatformIncidentStatus.RESOLVED);
            incident.setResolvedAt(LocalDateTime.now());
            incident.setResolvedBy(null);
            repository.save(incident);
            auditService.record(PlatformAuditAction.INCIDENT_AUTO_RESOLVED, null, true,
                    "INCIDENT", incident.getId(), "Health check recovered", null);
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<PlatformIncidentResponse> search(PlatformService service, PlatformIncidentStatus status,
                                                           IncidentSeverity severity, LocalDateTime fromDate,
                                                           LocalDateTime toDate, Pageable pageable) {
        return PageResponse.from(
                repository.search(service, status, severity, fromDate, toDate, pageable),
                this::toResponse);
    }

    @Transactional
    public PlatformIncidentResponse markInvestigating(Long id, Long actingAdminId, HttpServletRequest request) {
        PlatformIncident incident = require(id);
        if (incident.getStatus() != PlatformIncidentStatus.OPEN) {
            throw new BusinessException("Only an OPEN incident can be marked investigating.");
        }
        incident.setStatus(PlatformIncidentStatus.INVESTIGATING);
        repository.save(incident);
        audit(PlatformAuditAction.INCIDENT_INVESTIGATING, incident, actingAdminId, request);
        return toResponse(incident);
    }

    @Transactional
    public PlatformIncidentResponse resolve(Long id, Long actingAdminId, HttpServletRequest request) {
        PlatformIncident incident = require(id);
        if (incident.getStatus() == PlatformIncidentStatus.RESOLVED) {
            throw new BusinessException("This incident is already resolved.");
        }
        incident.setStatus(PlatformIncidentStatus.RESOLVED);
        incident.setResolvedAt(LocalDateTime.now());
        incident.setResolvedBy(actingAdminId);
        repository.save(incident);
        audit(PlatformAuditAction.INCIDENT_RESOLVED, incident, actingAdminId, request);
        return toResponse(incident);
    }

    @Transactional
    public PlatformIncidentResponse ignore(Long id, Long actingAdminId, HttpServletRequest request) {
        PlatformIncident incident = require(id);
        incident.setStatus(PlatformIncidentStatus.IGNORED);
        repository.save(incident);
        audit(PlatformAuditAction.INCIDENT_IGNORED, incident, actingAdminId, request);
        return toResponse(incident);
    }

    @Transactional
    public PlatformIncidentResponse reopen(Long id, Long actingAdminId, HttpServletRequest request) {
        PlatformIncident incident = require(id);
        if (incident.getStatus() == PlatformIncidentStatus.OPEN
                || incident.getStatus() == PlatformIncidentStatus.INVESTIGATING) {
            throw new BusinessException("This incident is already active.");
        }
        incident.setStatus(PlatformIncidentStatus.OPEN);
        incident.setResolvedAt(null);
        incident.setResolvedBy(null);
        incident.setLastSeen(LocalDateTime.now());
        repository.save(incident);
        audit(PlatformAuditAction.INCIDENT_REOPENED, incident, actingAdminId, request);
        return toResponse(incident);
    }

    private void audit(PlatformAuditAction action, PlatformIncident incident, Long actingAdminId, HttpServletRequest request) {
        PlatformAdmin actingAdmin = platformAdminRepository.getReferenceById(actingAdminId);
        auditService.record(action, actingAdmin, true, "INCIDENT", incident.getId(), incident.getTitle(), request);
    }

    private PlatformIncident require(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Incident", id));
    }

    private PlatformIncidentResponse toResponse(PlatformIncident incident) {
        return new PlatformIncidentResponse(
                incident.getId(), incident.getService(), incident.getSeverity(), incident.getTitle(),
                incident.getDescription(), incident.getStatus(), incident.getFirstSeen(), incident.getLastSeen(),
                incident.getOccurrenceCount(), incident.getResolvedAt(), incident.getResolvedBy());
    }
}
