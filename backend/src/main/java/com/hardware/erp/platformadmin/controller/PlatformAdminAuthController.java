package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.platformadmin.dto.*;
import com.hardware.erp.platformadmin.security.PlatformAdminPrincipal;
import com.hardware.erp.platformadmin.service.PlatformAdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Every path here is scoped by PlatformAdminSecurityConfig's securityMatcher
 * (/v1/platform-admin/**), a completely separate filter chain from the
 * tenant-facing AuthController - see that config class's javadoc.
 */
@RestController
@RequestMapping("/v1/platform-admin/auth")
@RequiredArgsConstructor
@Tag(name = "Platform Admin - Auth", description = "Sign in, MFA and sessions for Hardware ERP staff")
public class PlatformAdminAuthController {

    private final PlatformAdminAuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Step 1: password check",
            description = "Never returns a session. Returns a short-lived mfaToken and whether "
                        + "the account still needs to enroll in MFA - every platform admin account "
                        + "requires MFA, with no opt-out.")
    public ResponseEntity<ApiResponse<PlatformAdminLoginChallengeResponse>> login(
            @Valid @RequestBody PlatformAdminLoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request, httpRequest)));
    }

    @PostMapping("/mfa/verify")
    @Operation(summary = "Step 2: TOTP or backup code, for an account already enrolled",
            description = "Exchanges the mfaToken from /login plus a 6-digit code (or a 10-digit "
                        + "backup code) for a real session.")
    public ResponseEntity<ApiResponse<PlatformAdminSessionResponse>> verifyMfa(
            @Valid @RequestBody PlatformAdminMfaVerifyRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(authService.verifyMfa(request, httpRequest)));
    }

    @PostMapping("/mfa/enroll")
    @Operation(summary = "Start MFA enrollment",
            description = "Only reachable with an mfaToken issued for an account that has not yet "
                        + "enrolled. Generates a new TOTP secret and returns its QR code - calling "
                        + "this again before confirming replaces the pending secret.")
    public ResponseEntity<ApiResponse<PlatformAdminMfaEnrollResponse>> enroll(
            @Valid @RequestBody PlatformAdminMfaTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.enroll(request)));
    }

    @PostMapping("/mfa/enroll/confirm")
    @Operation(summary = "Confirm MFA enrollment",
            description = "Proves the admin actually captured the QR code. On success, enrollment "
                        + "is permanent, 10 backup codes are issued (shown exactly once), and a real "
                        + "session is returned immediately.")
    public ResponseEntity<ApiResponse<PlatformAdminMfaConfirmResponse>> confirmEnroll(
            @Valid @RequestBody PlatformAdminMfaVerifyRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(authService.confirmEnrollment(request, httpRequest)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the token pair",
            description = "Replaying an already-rotated token is treated as theft: every session "
                        + "for that admin is revoked and token_version is incremented.")
    public ResponseEntity<ApiResponse<PlatformAdminSessionResponse>> refresh(
            @RequestBody PlatformAdminRefreshRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request.refreshToken(), httpRequest)));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Sign out of this device only")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody PlatformAdminRefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.message("Signed out"));
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Sign out of every device")
    public ResponseEntity<ApiResponse<Void>> logoutAll() {
        authService.logoutAllDevices(currentAdminId());
        return ResponseEntity.ok(ApiResponse.message("Signed out of all devices"));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Current platform admin with role and effective permissions")
    public ResponseEntity<ApiResponse<PlatformAdminResponse>> me() {
        return ResponseEntity.ok(ApiResponse.ok(authService.currentAdmin(currentAdminId())));
    }

    private Long currentAdminId() {
        var principal = (PlatformAdminPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.getId();
    }
}
