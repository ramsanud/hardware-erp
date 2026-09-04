package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.platformadmin.dto.RazorpayConfigResponse;
import com.hardware.erp.platformadmin.dto.UpdateRazorpayConfigRequest;
import com.hardware.erp.platformadmin.security.PlatformAdminPrincipal;
import com.hardware.erp.platformadmin.service.PlatformRazorpayConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * CR-057 phase 12 - Platform Settings' Razorpay section. Fill in real
 * credentials here instead of redeploying with new environment variables -
 * see PlatformRazorpayConfigService's own javadoc for the precedence rule
 * against the RAZORPAY_* env vars.
 */
@RestController
@RequestMapping("/v1/platform-admin/settings/razorpay")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Platform Admin - Settings", description = "Razorpay billing credentials, configurable from the console")
public class PlatformAdminRazorpaySettingsController {

    private final PlatformRazorpayConfigService configService;

    @GetMapping
    @PreAuthorize("hasAuthority('BILLING_VIEW')")
    @Operation(summary = "Current Razorpay config - secrets never included, only whether each is set")
    public ResponseEntity<ApiResponse<RazorpayConfigResponse>> get() {
        return ResponseEntity.ok(ApiResponse.ok(configService.get()));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('BILLING_MANAGE')")
    @Operation(summary = "Update Razorpay config - audited, and never echoes a saved secret back")
    public ResponseEntity<ApiResponse<RazorpayConfigResponse>> update(
            @Valid @RequestBody UpdateRazorpayConfigRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(configService.update(request, currentAdminId(), httpRequest)));
    }

    private Long currentAdminId() {
        var principal = (PlatformAdminPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.getId();
    }
}
