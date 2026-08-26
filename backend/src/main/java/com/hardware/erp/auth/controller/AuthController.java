package com.hardware.erp.auth.controller;

import com.hardware.erp.auth.dto.*;
import com.hardware.erp.auth.service.AuthService;
import com.hardware.erp.auth.service.UserService;
import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.ErrorResponse;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.security.RefreshTokenCookieService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.security.captcha.CaptchaConfigResponse;
import com.hardware.erp.security.captcha.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "1. Authentication", description = "Sign in, tokens, sessions and passwords")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RefreshTokenCookieService cookieService;
    private final CaptchaService captchaService;

    // =================================================================
    // PUBLIC
    // =================================================================

    @PostMapping("/login")
    @Operation(
            summary = "Sign in with mobile number or email",
            description = """
                    Accepts either the mobile number or the email address in the
                    single `identifier` field.

                    A wrong password, an unknown account, a locked account and an
                    inactive account all return the identical 401 response, so the
                    endpoint cannot be used to discover which accounts exist.

                    Five failed attempts lock the account for 15 minutes.
                    Rate limited to 10/min per IP and 5/min per identifier.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Signed in",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class),
                            examples = @ExampleObject(name = "Success", value = """
                    {
                      "success": true,
                      "data": {
                        "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHYiOjB9.sig",
                        "refreshToken": null,
                        "tokenType": "Bearer",
                        "expiresInSeconds": 900,
                        "mustChangePassword": false,
                        "user": {
                          "id": 1, "fullName": "Saravanan M", "mobileNo": "9876543210",
                          "email": "owner@sarahardware.in", "employeeCode": "EMP001",
                          "roleId": 1, "roleCode": "OWNER", "roleName": "Owner",
                          "permissions": ["USER_MANAGE","PRODUCT_VIEW_COST"],
                          "status": "ACTIVE", "mustChangePassword": false
                        }
                      },
                      "timestamp": "2026-08-13T09:14:22.331+05:30"
                    }""")))
            ,
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                    {
                      "success": false,
                      "message": "Invalid credentials",
                      "code": "INVALID_CREDENTIALS",
                      "path": "/api/v1/auth/login",
                      "requestId": "6f1c2b7e-9a3d-4c5e-8b21-0d4f6a7c9e11",
                      "timestamp": "2026-08-13T09:14:22.331+05:30"
                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Validation failure",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                    {
                      "success": false,
                      "message": "Please correct the highlighted fields",
                      "code": "VALIDATION_ERROR",
                      "errors": { "password": "Password is required" },
                      "timestamp": "2026-08-13T09:14:22.331+05:30"
                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        // Before authentication, so a bot cannot use this endpoint to probe
        // passwords while failing the challenge. RateLimitFilter already ran.
        captchaService.verify(request.captchaToken(), SecurityUtils.clientIp(httpRequest));

        LoginResponse result = authService.login(request);
        cookieService.write(response, result.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(withMaskedRefreshToken(result)));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Rotate the token pair",
            description = """
                    Reads the refresh token from the HttpOnly cookie by default.

                    Every successful refresh revokes the presented token and
                    issues a new one. Replaying a token that was already rotated
                    is treated as theft: every session for that user is revoked
                    and `token_version` is incremented.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "New token pair issued"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Invalid, expired or reused token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Reuse detected", value = """
                    {
                      "success": false,
                      "message": "Your session is no longer valid. Please sign in again.",
                      "code": "TOKEN_REUSE",
                      "timestamp": "2026-08-13T09:20:00.000+05:30"
                    }""")))
    })
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        String raw = cookieService
                .read(httpRequest, request == null ? null : request.refreshToken())
                .orElse(null);

        LoginResponse result = authService.refresh(raw);
        cookieService.write(response, result.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(withMaskedRefreshToken(result)));
    }

    @GetMapping("/captcha-config")
    @Operation(
            summary = "Whether the sign-in page must show a security check",
            description = """
                    Public and unauthenticated - the login page needs this before
                    anyone has signed in.

                    Returns the Turnstile *site* key only; the secret stays on the
                    server. `enabled` is false unless the feature is switched on
                    AND both keys are configured, so an install that never sets
                    them keeps working exactly as before.""")
    public ApiResponse<CaptchaConfigResponse> captchaConfig() {
        return ApiResponse.ok(new CaptchaConfigResponse(captchaService.active(), captchaService.siteKey()));
    }

    @PostMapping("/forgot-password")
    @Operation(
            summary = "Request a password reset link",
            description = """
                    Always returns 200 with the same message, whether or not an
                    account exists. Any other behaviour would reveal which mobile
                    numbers and email addresses are registered.

                    Rate limited to 3/hour per IP and 3/hour per identifier.""")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.message(
                "If an account exists for that mobile number or email, "
                + "a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(
            summary = "Set a new password using a reset token",
            description = "The token is single-use and expires after 30 minutes. "
                        + "A successful reset signs the user out of every device.")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletResponse response) {
        authService.resetPassword(request);
        cookieService.clear(response);
        return ResponseEntity.ok(ApiResponse.message(
                "Password updated. You can now sign in."));
    }

    // =================================================================
    // AUTHENTICATED
    // =================================================================

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Sign out of this device only",
            description = "Revokes the presented refresh token. Other devices keep "
                        + "working, so closing the counter terminal does not sign "
                        + "the owner out on their phone.")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        cookieService.read(httpRequest, request == null ? null : request.refreshToken())
                .ifPresent(authService::logout);
        cookieService.clear(response);
        return ResponseEntity.ok(ApiResponse.message("Signed out"));
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Sign out of every device",
            description = "Revokes all refresh tokens and increments `token_version`, "
                        + "which invalidates every outstanding access token at once.")
    public ResponseEntity<ApiResponse<Void>> logoutAll(HttpServletResponse response) {
        AppUserDetails principal = SecurityUtils.requireCurrentUser();
        authService.logoutAllDevices(principal.getId());
        cookieService.clear(response);
        return ResponseEntity.ok(ApiResponse.message("Signed out of all devices"));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Current user with role and effective permissions")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        // Identity comes from the security context, never from a path or body.
        return ResponseEntity.ok(ApiResponse.ok(
                authService.currentUser(SecurityUtils.requireCurrentUser().getId())));
    }

    @PutMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Update own name and email",
            description = "Cannot change role or status. The target user is taken "
                        + "from the authenticated principal, so this endpoint can "
                        + "never edit another account.")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = SecurityUtils.requireCurrentUser().getId();
        return ResponseEntity.ok(ApiResponse.ok(
                "Profile updated", userService.updateOwnProfile(userId, request)));
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Change own password",
            description = "Signs the user out of every device, including this one.")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletResponse response) {
        authService.changePassword(SecurityUtils.requireCurrentUser().getId(), request);
        cookieService.clear(response);
        return ResponseEntity.ok(ApiResponse.message(
                "Password changed. Please sign in again."));
    }

    @GetMapping("/sessions")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Active sessions for the current user",
            description = "Shows IP, device and timestamps so a user can spot a "
                        + "device they do not recognise. Carries no token material.")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> sessions(
            HttpServletRequest httpRequest) {
        Long userId = SecurityUtils.requireCurrentUser().getId();
        String current = cookieService.read(httpRequest, null).orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(
                authService.activeSessions(userId, current)));
    }

    @DeleteMapping("/sessions/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Revoke one of your own sessions")
    public ResponseEntity<Void> revokeSession(@PathVariable Long id) {
        authService.revokeSession(SecurityUtils.requireCurrentUser().getId(), id);
        return ResponseEntity.noContent().build();
    }

    /** In cookie mode the refresh token must never appear in the response body. */
    private LoginResponse withMaskedRefreshToken(LoginResponse result) {
        return new LoginResponse(
                result.accessToken(),
                cookieService.bodyValue(result.refreshToken()),
                result.tokenType(),
                result.expiresInSeconds(),
                result.mustChangePassword(),
                result.user());
    }
}
