package com.hardware.erp.auth.service.impl;

import com.hardware.erp.auth.dto.*;
import com.hardware.erp.auth.entity.*;
import com.hardware.erp.auth.mapper.UserMapper;
import com.hardware.erp.auth.repository.PasswordResetTokenRepository;
import com.hardware.erp.auth.repository.RefreshTokenRepository;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.auth.service.AuthService;
import com.hardware.erp.auth.service.MailService;
import com.hardware.erp.auth.service.SecurityAuditService;
import com.hardware.erp.common.exception.AuthException;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.security.JwtService;
import com.hardware.erp.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final MailService mailService;
    private final SecurityAuditService auditService;

    @Value("${app.password-reset.token-validity-minutes:30}")
    private int resetValidityMinutes;

    @Value("${app.password-reset.reset-url-base}")
    private String resetUrlBase;

    // =================================================================
    // LOGIN
    // =================================================================

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String identifier = request.identifier().trim();

        Optional<User> maybeUser = userRepository.findByIdentifier(identifier);
        if (maybeUser.isEmpty()) {
            auditService.failure(AuditAction.LOGIN_FAILURE, null, null,
                    "Unknown identifier: " + identifier);
            throw AuthException.invalidCredentials();
        }

        User user = maybeUser.get();

        // Locked, inactive and wrong-password all raise the identical exception.
        // Any difference in message, code or status makes login an account
        // enumeration oracle.
        if (user.isLocked()) {
            auditService.failure(AuditAction.LOGIN_FAILURE, user.getId(), user.getFullName(),
                    "Account locked until " + user.getLockedUntil());
            throw AuthException.invalidCredentials();
        }
        if (!user.isActive()) {
            auditService.failure(AuditAction.LOGIN_FAILURE, user.getId(), user.getFullName(),
                    "Account status " + user.getStatus());
            throw AuthException.invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean nowLocked = user.registerFailedLogin();
            userRepository.save(user);
            auditService.failure(AuditAction.LOGIN_FAILURE, user.getId(), user.getFullName(),
                    "Wrong password, attempt " + user.getFailedLoginAttempts());
            if (nowLocked) {
                auditService.failure(AuditAction.ACCOUNT_LOCKED, user.getId(),
                        user.getFullName(), "Locked for " + User.LOCK_MINUTES + " minutes");
            }
            throw AuthException.invalidCredentials();
        }

        user.registerSuccessfulLogin();
        userRepository.save(user);
        auditService.success(AuditAction.LOGIN_SUCCESS, user.getId(), user.getFullName(),
                "USER", user.getId());

        return issueTokens(user);
    }

    private LoginResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getTokenVersion());
        String rawRefreshToken = jwtService.generateRefreshToken();
        HttpServletRequest request = currentRequest();

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(rawRefreshToken))
                .expiresAt(LocalDateTime.now().plusDays(jwtService.refreshTokenDays()))
                .ipAddress(request != null ? SecurityUtils.clientIp(request) : null)
                .userAgent(request != null ? SecurityUtils.userAgent(request) : null)
                .lastUsedAt(LocalDateTime.now())
                .build());

        return new LoginResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtService.accessTokenSeconds(),
                user.isMustChangePassword(),
                userMapper.toUserResponse(user));
    }

    // =================================================================
    // REFRESH - rotation with reuse detection
    // =================================================================

    /**
     * {@code noRollbackFor = AuthException.class} is load-bearing, not tidy-up
     * (BUG-AUTH-009). The theft branch below *writes* - it revokes every
     * session and bumps tokenVersion - and then reports the theft by throwing.
     * Under the default rollback rules that throw undid the very revocation it
     * was announcing, so a stolen refresh token stayed usable. Every other
     * AuthException path in this method writes nothing, so committing on the
     * way out is a no-op for them.
     */
    @Override
    @Transactional(noRollbackFor = AuthException.class)
    public LoginResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthException("Refresh token is required", "INVALID_REFRESH_TOKEN");
        }

        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(jwtService.hashToken(rawRefreshToken))
                .orElseThrow(() -> new AuthException(
                        "Invalid refresh token", "INVALID_REFRESH_TOKEN"));

        User user = stored.getUser();

        if (stored.isRevoked()) {
            // Only a ROTATED token being replayed is evidence of theft: the
            // legitimate client was handed the replacement, so whoever still
            // holds this one captured it. A token revoked by LOGOUT,
            // LOGOUT_ALL, a password change or an admin session revoke is
            // simply a finished session - replaying it is what a stale browser
            // tab does, and answering that by signing the user out of every
            // other device would be a denial of service on their own account.
            if (stored.getRevokedReason() != RevokedReason.ROTATED) {
                throw new AuthException(
                        "Your session has ended. Please sign in again.", "INVALID_REFRESH_TOKEN");
            }

            // Re-read the user as a managed entity. stored.getUser() is a lazy
            // proxy, and revokeAllForUser below is @Modifying(clearAutomatically)
            // - it detaches everything in the persistence context, after which
            // touching that proxy throws LazyInitializationException. That is
            // exactly what used to happen here, turning the theft response into
            // an unhandled 500 (BUG-AUTH-009).
            Long userId = user.getId();
            User owner = userRepository.findById(userId).orElseThrow(
                    () -> new AuthException("Invalid refresh token", "INVALID_REFRESH_TOKEN"));
            String ownerName = owner.getFullName();

            log.warn("Refresh token reuse detected for user {}", userId);

            // tokenVersion first, while the context is still intact; the bulk
            // revoke clears it, so no entity may be touched after that call.
            owner.invalidateAllTokens();
            userRepository.saveAndFlush(owner);

            refreshTokenRepository.revokeAllForUser(
                    userId, RevokedReason.REUSE_DETECTED, LocalDateTime.now());

            auditService.failure(AuditAction.REFRESH_TOKEN_REUSE_DETECTED, userId,
                    ownerName, "All sessions revoked");
            throw new AuthException(
                    "Your session is no longer valid. Please sign in again.", "TOKEN_REUSE");
        }

        if (stored.isExpired()) {
            throw new AuthException(
                    "Your session has expired. Please sign in again.", "REFRESH_TOKEN_EXPIRED");
        }
        if (!user.isActive() || user.isLocked()) {
            throw AuthException.invalidCredentials();
        }

        String newRawToken = jwtService.generateRefreshToken();
        HttpServletRequest request = currentRequest();

        RefreshToken replacement = refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(newRawToken))
                .expiresAt(LocalDateTime.now().plusDays(jwtService.refreshTokenDays()))
                .ipAddress(request != null ? SecurityUtils.clientIp(request) : null)
                .userAgent(request != null ? SecurityUtils.userAgent(request) : null)
                .lastUsedAt(LocalDateTime.now())
                .build());

        stored.revoke(RevokedReason.ROTATED);
        stored.setReplacedByTokenId(replacement.getId());
        stored.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);

        auditService.success(AuditAction.TOKEN_REFRESHED, user.getId(), user.getFullName(),
                "REFRESH_TOKEN", replacement.getId());

        return new LoginResponse(
                jwtService.generateAccessToken(user.getId(), user.getTokenVersion()),
                newRawToken,
                "Bearer",
                jwtService.accessTokenSeconds(),
                user.isMustChangePassword(),
                userMapper.toUserResponse(user));
    }

    // =================================================================
    // LOGOUT
    // =================================================================

    /**
     * This device only. token_version is deliberately NOT bumped - closing the
     * counter terminal must not sign the owner out on their phone.
     */
    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(jwtService.hashToken(rawRefreshToken))
                .ifPresent(token -> {
                    token.revoke(RevokedReason.LOGOUT);
                    refreshTokenRepository.save(token);
                    auditService.success(AuditAction.LOGOUT, token.getUser().getId(),
                            token.getUser().getFullName(), "REFRESH_TOKEN", token.getId());
                });
        // Silent on an unknown token: logout must never fail, or a client with a
        // stale token can never clear its state.
    }

    @Override
    @Transactional
    public void logoutAllDevices(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        refreshTokenRepository.revokeAllForUser(
                userId, RevokedReason.LOGOUT_ALL, LocalDateTime.now());
        user.invalidateAllTokens();
        userRepository.save(user);

        auditService.success(AuditAction.LOGOUT_ALL, userId, user.getFullName(),
                "USER", userId);
    }

    // =================================================================
    // SESSIONS
    // =================================================================

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> activeSessions(Long userId, String currentRawRefreshToken) {
        String currentHash = (currentRawRefreshToken == null || currentRawRefreshToken.isBlank())
                ? null : jwtService.hashToken(currentRawRefreshToken);

        return refreshTokenRepository.findActiveSessions(userId, LocalDateTime.now()).stream()
                .map(token -> userMapper.toSessionResponse(
                        token, token.getTokenHash().equals(currentHash)))
                .toList();
    }

    @Override
    @Transactional
    public void revokeSession(Long userId, Long sessionId) {
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));

        // Ownership check: a user must not be able to revoke another user's
        // session by guessing an id.
        if (!token.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Session", sessionId);
        }

        token.revoke(RevokedReason.SESSION_REVOKED);
        refreshTokenRepository.save(token);
        auditService.success(AuditAction.SESSION_REVOKED, userId, null,
                "REFRESH_TOKEN", sessionId);
    }

    // =================================================================
    // PASSWORD
    // =================================================================

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditService.failure(AuditAction.PASSWORD_CHANGED, userId, user.getFullName(),
                    "Current password incorrect");
            throw new BusinessException("Current password is incorrect",
                    HttpStatus.BAD_REQUEST, "WRONG_PASSWORD");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("New password must be different from the current one");
        }

        user.applyNewPassword(passwordEncoder.encode(request.newPassword()), false);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(
                userId, RevokedReason.PASSWORD_CHANGED, LocalDateTime.now());

        auditService.success(AuditAction.PASSWORD_CHANGED, userId, user.getFullName(),
                "USER", userId);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<User> maybeUser = userRepository.findByIdentifier(request.identifier().trim());

        // Returns normally in every branch. Revealing whether an account exists
        // would turn this endpoint into an enumeration oracle, so the caller
        // always receives the same message.
        if (maybeUser.isEmpty()) {
            log.info("Password reset requested for an unknown identifier");
            return;
        }
        User user = maybeUser.get();
        if (!user.isActive()) {
            return;
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("User {} has no email address; reset link cannot be sent", user.getId());
            return;
        }

        resetTokenRepository.invalidateAllForUser(user.getId(), LocalDateTime.now());

        String rawToken = jwtService.generateRefreshToken();
        HttpServletRequest servletRequest = currentRequest();
        resetTokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(resetValidityMinutes))
                .ipAddress(servletRequest != null
                        ? SecurityUtils.clientIp(servletRequest) : null)
                .build());

        String url = resetUrlBase + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        mailService.sendPasswordResetLink(user.getEmail(), user.getFullName(), url);

        auditService.success(AuditAction.PASSWORD_RESET_REQUESTED, user.getId(),
                user.getFullName(), "USER", user.getId());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = resetTokenRepository
                .findByTokenHash(jwtService.hashToken(request.token()))
                .orElseThrow(() -> new BusinessException(
                        "This reset link is invalid or has already been used",
                        HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN"));

        if (!token.isUsable()) {
            throw new BusinessException(
                    "This reset link has expired or has already been used. "
                    + "Please request a new one.",
                    HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN");
        }

        User user = token.getUser();
        user.applyNewPassword(passwordEncoder.encode(request.newPassword()), false);
        userRepository.save(user);

        token.markUsed();
        resetTokenRepository.save(token);

        refreshTokenRepository.revokeAllForUser(
                user.getId(), RevokedReason.PASSWORD_RESET, LocalDateTime.now());

        auditService.success(AuditAction.PASSWORD_RESET, user.getId(), user.getFullName(),
                "USER", user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(userMapper::toUserResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
