package com.hardware.erp.platformadmin.service;

import com.hardware.erp.auth.entity.RevokedReason;
import com.hardware.erp.common.exception.AuthException;
import com.hardware.erp.common.image.QrCodeGenerator;
import com.hardware.erp.platformadmin.dto.*;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAdminRefreshToken;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.mapper.PlatformAdminMapper;
import com.hardware.erp.platformadmin.repository.PlatformAdminRefreshTokenRepository;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.platformadmin.security.MfaTokenPurpose;
import com.hardware.erp.platformadmin.security.PlatformAdminJwtService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.security.totp.TotpService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Login is always exactly two factors here - there is no "MFA optional"
 * path. An account with mfaEnabled=false gets an enrollment challenge
 * instead of a session on its very next successful password check, and
 * never gets a session before enrollment is confirmed (PLATFORM_ADMIN_SECURITY.md).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAdminAuthService {

    private static final String ISSUER_LABEL = "Hardware ERP Platform Admin";

    private final PlatformAdminRepository platformAdminRepository;
    private final PlatformAdminRefreshTokenRepository refreshTokenRepository;
    private final PlatformAdminJwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final BackupCodeService backupCodeService;
    private final PlatformAuditService auditService;
    private final PlatformAdminMapper mapper;

    @Transactional
    public PlatformAdminLoginChallengeResponse login(PlatformAdminLoginRequest request,
                                                      HttpServletRequest httpRequest) {
        Optional<PlatformAdmin> found = platformAdminRepository.findByEmailIgnoreCase(request.email().trim());

        if (found.isEmpty()) {
            auditService.failure(PlatformAuditAction.LOGIN_FAILURE, null,
                    "Unknown email", httpRequest);
            throw AuthException.invalidCredentials();
        }

        PlatformAdmin admin = found.get();

        if (admin.isLocked() || !admin.isActive()
                || !passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            boolean justLocked = !admin.isLocked() && admin.isActive()
                    && admin.registerFailedLogin();
            platformAdminRepository.save(admin);
            auditService.failure(
                    justLocked ? PlatformAuditAction.ACCOUNT_LOCKED : PlatformAuditAction.LOGIN_FAILURE,
                    admin, "Password check failed", httpRequest);
            throw AuthException.invalidCredentials();
        }

        admin.registerSuccessfulLogin();
        platformAdminRepository.save(admin);

        MfaTokenPurpose purpose = admin.isMfaEnabled() ? MfaTokenPurpose.LOGIN : MfaTokenPurpose.ENROLL;
        String mfaToken = jwtService.generateMfaToken(admin.getId(), purpose);

        auditService.success(PlatformAuditAction.LOGIN_MFA_REQUIRED, admin, httpRequest);

        return new PlatformAdminLoginChallengeResponse(
                mfaToken, purpose == MfaTokenPurpose.ENROLL, jwtService.mfaTokenSeconds());
    }

    @Transactional
    public PlatformAdminSessionResponse verifyMfa(PlatformAdminMfaVerifyRequest request,
                                                   HttpServletRequest httpRequest) {
        PlatformAdmin admin = requireChallenge(request.mfaToken(), MfaTokenPurpose.LOGIN);

        boolean valid = totpService.verifyCode(admin.getTotpSecret(), request.code())
                || backupCodeService.consume(admin, request.code());

        if (!valid) {
            auditService.failure(PlatformAuditAction.MFA_CHALLENGE_FAILED, admin,
                    "Invalid TOTP or backup code", httpRequest);
            throw new AuthException("Invalid verification code", "INVALID_MFA_CODE");
        }

        auditService.success(PlatformAuditAction.LOGIN_SUCCESS, admin, httpRequest);
        return issueSession(admin, httpRequest);
    }

    @Transactional
    public PlatformAdminMfaEnrollResponse enroll(PlatformAdminMfaTokenRequest request) {
        PlatformAdmin admin = requireChallenge(request.mfaToken(), MfaTokenPurpose.ENROLL);
        if (admin.isMfaEnabled()) {
            throw new AuthException("MFA is already enrolled for this account", "MFA_ALREADY_ENROLLED");
        }

        String secret = totpService.generateSecret();
        admin.beginMfaEnrollment(secret);
        platformAdminRepository.save(admin);

        String otpAuthUri = totpService.otpAuthUri(ISSUER_LABEL, admin.getEmail(), secret);
        String qrBase64 = Base64.getEncoder().encodeToString(QrCodeGenerator.pngBytes(otpAuthUri));

        auditService.success(PlatformAuditAction.MFA_ENROLLMENT_STARTED, admin, null);
        return new PlatformAdminMfaEnrollResponse(otpAuthUri, qrBase64, secret);
    }

    @Transactional
    public PlatformAdminMfaConfirmResponse confirmEnrollment(PlatformAdminMfaVerifyRequest request,
                                                              HttpServletRequest httpRequest) {
        PlatformAdmin admin = requireChallenge(request.mfaToken(), MfaTokenPurpose.ENROLL);
        if (admin.getTotpSecret() == null) {
            throw new AuthException("Call /mfa/enroll first", "MFA_NOT_STARTED");
        }
        if (!totpService.verifyCode(admin.getTotpSecret(), request.code())) {
            auditService.failure(PlatformAuditAction.MFA_CHALLENGE_FAILED, admin,
                    "Invalid enrollment code", httpRequest);
            throw new AuthException("Invalid verification code", "INVALID_MFA_CODE");
        }

        admin.confirmMfaEnrollment();
        admin.registerSuccessfulLogin();
        platformAdminRepository.save(admin);

        List<String> backupCodes = backupCodeService.issueNewSet(admin);

        auditService.success(PlatformAuditAction.MFA_ENROLLED, admin, httpRequest);
        auditService.success(PlatformAuditAction.LOGIN_SUCCESS, admin, httpRequest);

        return new PlatformAdminMfaConfirmResponse(issueSession(admin, httpRequest), backupCodes);
    }

    /**
     * {@code noRollbackFor = AuthException.class}: mirrors AuthServiceImpl.refresh
     * exactly (BUG-AUTH-009) - the reuse-detected branch revokes every session
     * and must keep that write even though it also throws to report the theft.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public PlatformAdminSessionResponse refresh(String rawRefreshToken, HttpServletRequest httpRequest) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthException("Refresh token is required", "INVALID_REFRESH_TOKEN");
        }

        PlatformAdminRefreshToken stored = refreshTokenRepository
                .findByTokenHash(jwtService.hashToken(rawRefreshToken))
                .orElseThrow(() -> new AuthException("Invalid refresh token", "INVALID_REFRESH_TOKEN"));

        Long adminId = stored.getAdmin().getId();

        if (stored.isRevoked()) {
            if (stored.getRevokedReason() != RevokedReason.ROTATED) {
                throw new AuthException("Your session has ended. Please sign in again.",
                        "INVALID_REFRESH_TOKEN");
            }

            PlatformAdmin owner = platformAdminRepository.findById(adminId).orElseThrow(
                    () -> new AuthException("Invalid refresh token", "INVALID_REFRESH_TOKEN"));

            log.warn("Platform admin refresh token reuse detected for admin {}", adminId);

            owner.invalidateAllTokens();
            platformAdminRepository.saveAndFlush(owner);

            refreshTokenRepository.revokeAllForAdmin(adminId, RevokedReason.REUSE_DETECTED, LocalDateTime.now());

            auditService.failure(PlatformAuditAction.REFRESH_TOKEN_REUSE_DETECTED, owner,
                    "All sessions revoked", httpRequest);
            throw new AuthException("Your session is no longer valid. Please sign in again.", "TOKEN_REUSE");
        }

        if (stored.isExpired()) {
            throw new AuthException("Your session has expired. Please sign in again.",
                    "REFRESH_TOKEN_EXPIRED");
        }

        PlatformAdmin admin = platformAdminRepository.findById(adminId).orElseThrow(
                () -> new AuthException("Invalid refresh token", "INVALID_REFRESH_TOKEN"));
        if (!admin.isActive() || admin.isLocked()) {
            throw AuthException.invalidCredentials();
        }

        String newRawToken = jwtService.generateRefreshToken();
        PlatformAdminRefreshToken replacement = refreshTokenRepository.save(PlatformAdminRefreshToken.builder()
                .admin(admin)
                .tokenHash(jwtService.hashToken(newRawToken))
                .expiresAt(LocalDateTime.now().plusDays(jwtService.refreshTokenDays()))
                .ipAddress(httpRequest != null ? SecurityUtils.clientIp(httpRequest) : null)
                .userAgent(httpRequest != null ? SecurityUtils.userAgent(httpRequest) : null)
                .lastUsedAt(LocalDateTime.now())
                .build());

        stored.revoke(RevokedReason.ROTATED);
        stored.setReplacedByTokenId(replacement.getId());
        stored.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);

        auditService.success(PlatformAuditAction.TOKEN_REFRESHED, admin, httpRequest);

        return new PlatformAdminSessionResponse(
                jwtService.generateAccessToken(admin.getId(), admin.getTokenVersion()),
                newRawToken, "Bearer", jwtService.accessTokenSeconds(), mapper.toResponse(admin));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(jwtService.hashToken(rawRefreshToken))
                .ifPresent(token -> {
                    token.revoke(RevokedReason.LOGOUT);
                    refreshTokenRepository.save(token);
                    auditService.success(PlatformAuditAction.LOGOUT, token.getAdmin(), null);
                });
    }

    @Transactional
    public void logoutAllDevices(Long adminId) {
        PlatformAdmin admin = platformAdminRepository.findById(adminId).orElseThrow(
                () -> new AuthException("Not authenticated", "UNAUTHENTICATED"));
        admin.invalidateAllTokens();
        platformAdminRepository.save(admin);
        refreshTokenRepository.revokeAllForAdmin(adminId, RevokedReason.LOGOUT_ALL, LocalDateTime.now());
        auditService.success(PlatformAuditAction.LOGOUT_ALL, admin, null);
    }

    public PlatformAdminResponse currentAdmin(Long adminId) {
        PlatformAdmin admin = platformAdminRepository.findById(adminId).orElseThrow(
                () -> new AuthException("Not authenticated", "UNAUTHENTICATED"));
        return mapper.toResponse(admin);
    }

    private PlatformAdmin requireChallenge(String mfaToken, MfaTokenPurpose expectedPurpose) {
        Claims claims = jwtService.parse(mfaToken).orElseThrow(
                () -> new AuthException("Invalid or expired verification session", "MFA_TOKEN_INVALID"));

        MfaTokenPurpose purpose = jwtService.purposeFrom(claims).orElseThrow(
                () -> new AuthException("Invalid or expired verification session", "MFA_TOKEN_INVALID"));
        if (purpose != expectedPurpose) {
            throw new AuthException("Invalid or expired verification session", "MFA_TOKEN_INVALID");
        }

        Long adminId = jwtService.adminIdFrom(claims).orElseThrow(
                () -> new AuthException("Invalid or expired verification session", "MFA_TOKEN_INVALID"));

        PlatformAdmin admin = platformAdminRepository.findById(adminId).orElseThrow(
                () -> new AuthException("Invalid or expired verification session", "MFA_TOKEN_INVALID"));
        if (!admin.isActive() || admin.isLocked()) {
            throw AuthException.invalidCredentials();
        }
        return admin;
    }

    private PlatformAdminSessionResponse issueSession(PlatformAdmin admin, HttpServletRequest httpRequest) {
        String rawRefreshToken = jwtService.generateRefreshToken();
        refreshTokenRepository.save(PlatformAdminRefreshToken.builder()
                .admin(admin)
                .tokenHash(jwtService.hashToken(rawRefreshToken))
                .expiresAt(LocalDateTime.now().plusDays(jwtService.refreshTokenDays()))
                .ipAddress(httpRequest != null ? SecurityUtils.clientIp(httpRequest) : null)
                .userAgent(httpRequest != null ? SecurityUtils.userAgent(httpRequest) : null)
                .lastUsedAt(LocalDateTime.now())
                .build());

        return new PlatformAdminSessionResponse(
                jwtService.generateAccessToken(admin.getId(), admin.getTokenVersion()),
                rawRefreshToken, "Bearer", jwtService.accessTokenSeconds(), mapper.toResponse(admin));
    }
}
