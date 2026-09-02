package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.platformadmin.dto.CreateFeatureFlagRequest;
import com.hardware.erp.platformadmin.dto.FeatureFlagResponse;
import com.hardware.erp.platformadmin.security.PlatformAdminPrincipal;
import com.hardware.erp.platformadmin.service.FeatureFlagService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/platform-admin/feature-flags")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Platform Admin - Feature Flags")
public class PlatformAdminFeatureFlagController {

    private final FeatureFlagService featureFlagService;

    @GetMapping
    @PreAuthorize("hasAuthority('FEATURE_FLAG_VIEW')")
    public ResponseEntity<ApiResponse<List<FeatureFlagResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(featureFlagService.list()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('FEATURE_FLAG_MANAGE')")
    public ApiResponse<FeatureFlagResponse> create(@Valid @RequestBody CreateFeatureFlagRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(featureFlagService.create(request, currentAdminId(), httpRequest));
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('FEATURE_FLAG_MANAGE')")
    public ApiResponse<FeatureFlagResponse> enable(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(featureFlagService.setEnabled(id, true, currentAdminId(), request));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('FEATURE_FLAG_MANAGE')")
    public ApiResponse<FeatureFlagResponse> disable(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(featureFlagService.setEnabled(id, false, currentAdminId(), request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FEATURE_FLAG_MANAGE')")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        featureFlagService.delete(id, currentAdminId(), request);
        return ApiResponse.message("Feature flag deleted");
    }

    private Long currentAdminId() {
        var principal = (PlatformAdminPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.getId();
    }
}
