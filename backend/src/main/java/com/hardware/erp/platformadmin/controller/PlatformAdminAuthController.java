package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.platformadmin.dto.*;
import com.hardware.erp.platformadmin.security.PlatformAdminPrincipal;
import com.hardware.erp.platformadmin.security.PlatformAdminRefreshTokenCookieService;
import com.hardware.erp.platformadmin.service.PlatformAdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.hardware.erp.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

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
    private final PlatformAdminRefreshTokenCookieService refreshCookies;

    /**
     * CR-065. Every path that mints a session funnels through here, so the
     * refresh token is put in the HttpOnly cookie and stripped from the body
     * in exactly one place. Missing one of them was the whole shape of
     * BUG-SEC-006 - the console had four session-issuing endpoints and all
     * four returned the raw token to JavaScript.
     */
    private PlatformAdminSessionResponse withCookie(PlatformAdminSessionResponse session,
                                                    HttpServletResponse response) {
        refreshCookies.write(response, session.refreshToken());
        return new PlatformAdminSessionResponse(
                session.accessToken(),
                refreshCookies.bodyValue(session.refreshToken()),
                session.tokenType(),
                session.expiresInSeconds(),
                session.admin());
    }

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
            @Valid @RequestBody PlatformAdminMfaVerifyRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return ResponseEntity.ok(ApiResponse.ok(
                withCookie(authService.verifyMfa(request, httpRequest), httpResponse)));
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
            @Valid @RequestBody PlatformAdminMfaVerifyRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        PlatformAdminMfaConfirmResponse result = authService.confirmEnrollment(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(new PlatformAdminMfaConfirmResponse(
                withCookie(result.session(), httpResponse), result.backupCodes())));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the token pair",
            description = "Replaying an already-rotated token is treated as theft: every session "
                        + "for that admin is revoked and token_version is incremented.")
    public ResponseEntity<ApiResponse<PlatformAdminSessionResponse>> refresh(
            @RequestBody(required = false) PlatformAdminRefreshRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // Body optional: in cookie mode the browser sends no body at all.
        String raw = refreshCookies.read(httpRequest, request == null ? null : request.refreshToken())
                .orElseThrow(() -> new BusinessException("No refresh token supplied",
                        HttpStatus.UNAUTHORIZED, "NO_REFRESH_TOKEN"));
        return ResponseEntity.ok(ApiResponse.ok(
                withCookie(authService.refresh(raw, httpRequest), httpResponse)));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Sign out of this device only")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) PlatformAdminRefreshRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // The cookie is cleared whatever the revocation does - a token the
        // browser can still send is worse than one the server has forgotten.
        refreshCookies.read(httpRequest, request == null ? null : request.refreshToken())
                .ifPresent(authService::logout);
        refreshCookies.clear(httpResponse);
        return ResponseEntity.ok(ApiResponse.message("Signed out"));
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Sign out of every device")
    public ResponseEntity<ApiResponse<Void>> logoutAll(HttpServletResponse httpResponse) {
        authService.logoutAllDevices(currentAdminId());
        // This device is one of the devices being signed out.
        refreshCookies.clear(httpResponse);
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
