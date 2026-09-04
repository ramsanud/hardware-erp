package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.platformadmin.dto.CreatePlatformAdminRequest;
import com.hardware.erp.platformadmin.dto.PlatformAdminResponse;
import com.hardware.erp.platformadmin.service.PlatformAdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SUPER_ADMIN-only, proving the 7-role RBAC model end to end. Deliberately
 * minimal for Phase 1 (create + list) - deactivate, role change and
 * self-service profile editing are a later phase, same as tenant UserService.
 */
@RestController
@RequestMapping("/v1/platform-admin/admins")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Platform Admin - Staff", description = "Manage other platform admin accounts (SUPER_ADMIN only)")
public class PlatformAdminUserController {

    private final PlatformAdminUserService userService;

    @PostMapping
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN_MANAGE')")
    @Operation(summary = "Create a platform admin account",
            description = "The new account starts with MFA not yet enrolled and must complete "
                        + "enrollment on its first login before it gets a session.")
    public ResponseEntity<ApiResponse<PlatformAdminResponse>> create(
            @Valid @RequestBody CreatePlatformAdminRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Platform admin created", userService.create(request, httpRequest)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN_MANAGE')")
    @Operation(summary = "List all platform admin accounts")
    public ResponseEntity<ApiResponse<List<PlatformAdminResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(userService.list()));
    }
}
