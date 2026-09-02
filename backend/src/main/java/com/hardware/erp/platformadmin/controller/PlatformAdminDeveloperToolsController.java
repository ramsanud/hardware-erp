package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.platformadmin.dto.BackgroundJobResponse;
import com.hardware.erp.platformadmin.dto.DatabaseDiagnosticsResponse;
import com.hardware.erp.platformadmin.security.PlatformAdminPrincipal;
import com.hardware.erp.platformadmin.service.DeveloperToolsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/platform-admin/developer-tools")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Platform Admin - Developer Tools")
public class PlatformAdminDeveloperToolsController {

    private final DeveloperToolsService developerToolsService;

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('DEVELOPER_TOOLS_VIEW')")
    public ResponseEntity<ApiResponse<List<BackgroundJobResponse>>> jobs() {
        return ResponseEntity.ok(ApiResponse.ok(developerToolsService.listJobs()));
    }

    @PostMapping("/jobs/{jobName}/retry")
    @PreAuthorize("hasAuthority('DEVELOPER_TOOLS_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> retryJob(@PathVariable String jobName, HttpServletRequest request) {
        var principal = (PlatformAdminPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        developerToolsService.retryJob(jobName, principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.message("Job retried"));
    }

    @GetMapping("/database")
    @PreAuthorize("hasAuthority('DEVELOPER_TOOLS_VIEW')")
    public ResponseEntity<ApiResponse<DatabaseDiagnosticsResponse>> database() {
        return ResponseEntity.ok(ApiResponse.ok(developerToolsService.databaseDiagnostics()));
    }
}
