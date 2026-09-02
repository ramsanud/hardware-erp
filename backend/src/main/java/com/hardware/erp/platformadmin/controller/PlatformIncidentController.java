package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.platformadmin.dto.PlatformIncidentResponse;
import com.hardware.erp.platformadmin.entity.IncidentSeverity;
import com.hardware.erp.platformadmin.entity.PlatformIncidentStatus;
import com.hardware.erp.platformadmin.entity.PlatformService;
import com.hardware.erp.platformadmin.security.PlatformAdminPrincipal;
import com.hardware.erp.platformadmin.service.PlatformIncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/v1/platform-admin/incidents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Platform Admin - Incidents")
public class PlatformIncidentController {

    private final PlatformIncidentService incidentService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_HEALTH_VIEW')")
    public ResponseEntity<ApiResponse<PageResponse<PlatformIncidentResponse>>> list(
            @RequestParam(required = false) PlatformService service,
            @RequestParam(required = false) PlatformIncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                incidentService.search(service, status, severity, fromDate, toDate, pageable)));
    }

    @PostMapping("/{id}/investigating")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    @Operation(summary = "Mark an OPEN incident as being investigated")
    public ResponseEntity<ApiResponse<PlatformIncidentResponse>> markInvestigating(
            @PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.markInvestigating(id, currentAdminId(), request)));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    public ResponseEntity<ApiResponse<PlatformIncidentResponse>> resolve(
            @PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.resolve(id, currentAdminId(), request)));
    }

    @PostMapping("/{id}/ignore")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    public ResponseEntity<ApiResponse<PlatformIncidentResponse>> ignore(
            @PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.ignore(id, currentAdminId(), request)));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    public ResponseEntity<ApiResponse<PlatformIncidentResponse>> reopen(
            @PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.reopen(id, currentAdminId(), request)));
    }

    private Long currentAdminId() {
        var principal = (PlatformAdminPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.getId();
    }
}
