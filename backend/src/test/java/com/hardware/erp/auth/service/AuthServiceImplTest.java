package com.hardware.erp.auth.service;

import com.hardware.erp.auth.dto.*;
import com.hardware.erp.auth.entity.*;
import com.hardware.erp.auth.mapper.UserMapper;
import com.hardware.erp.auth.repository.PasswordResetTokenRepository;
import com.hardware.erp.auth.repository.RefreshTokenRepository;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.auth.service.impl.AuthServiceImpl;
import com.hardware.erp.common.exception.AuthException;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.security.JwtProperties;
import com.hardware.erp.security.JwtService;
import com.hardware.erp.security.SecurityProperties;
import com.hardware.erp.security.totp.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Fast unit tests with mocked repositories. Behaviour that depends on the real
 * database - locking, constraints, transactions - is covered by the ITs.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    private static final String PASSWORD = "Correct@2026";

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private MailService mailService;
    @Mock private SecurityAuditService auditService;
    @Mock private UserBackupCodeService backupCodeService;

    @Spy private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    @Spy private UserMapper userMapper = new UserMapper();
    @Spy private TotpService totpService = new TotpService();
    @Spy private JwtService jwtService = new JwtService(new JwtProperties(
            "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLTMyYnl0ZXMh", "hardware-erp", 15, 7, 10));

    /**
     * CR-060 - mfaRequired=true, the default and what every pre-CR-060 test in
     * this class assumes. The two tests that cover MFA being switched off stub
     * this spy rather than changing the field, so the rest of the class keeps
     * exercising the CR-058 mandatory-MFA path unchanged.
     */
    @Spy private SecurityProperties securityProperties = new SecurityProperties(
            SecurityProperties.RefreshTokenTransport.COOKIE, "erp_refresh_token",
            true, true, List.of("http://localhost:5173"));

    @InjectMocks private AuthServiceImpl authService;

    private User user;

    /**
     * CR-058 - login now only clears the first factor, so every test that
     * needs a real session completes the mandatory MFA challenge the same
     * way a client does.
     */
    private LoginResponse signIn(String identifier) {
        LoginChallengeResponse challenge = authService.login(new LoginRequest(identifier, PASSWORD));
        if (challenge.enrollmentRequired()) {
            MfaEnrollResponse enroll = authService.enrollMfa(new MfaTokenRequest(challenge.mfaToken()));
            return authService.confirmMfaEnroll(new MfaVerifyRequest(
                    challenge.mfaToken(), totpService.currentCode(enroll.secretBase32()))).session();
        }
        return authService.verifyMfa(new MfaVerifyRequest(
                challenge.mfaToken(), totpService.currentCode(user.getTotpSecret())));
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "resetValidityMinutes", 30);
        ReflectionTestUtils.setField(authService, "resetUrlBase", "http://localhost/reset");

        Permission perm = Permission.builder()
                .id(1L).code(PermissionCode.USER_MANAGE).name("Manage users")
                .moduleCode("AUTH").displayOrder(20).build();

        Role role = Role.builder()
                .id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE)
                .permissions(new LinkedHashSet<>(Set.of(perm)))
                .build();

        user = User.builder()
                .id(10L).role(role)
                .fullName("Saravanan Murugan")
                .mobileNo("9876543210")
                .email("owner@sarahardware.in")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .status(UserStatus.ACTIVE)
                .tokenVersion(0).failedLoginAttempts(0)
                .build();

        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        // The MFA challenge token carries the user id, so completing it
        // re-reads the user by id rather than by identifier.
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> {
            RefreshToken t = i.getArgument(0);
            if (t.getId() == null) t.setId(99L);
            return t;
        });
    }

    // =============================================================
    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("a correct password returns an MFA challenge, not a session (CR-058)")
        void passwordAloneNeverIssuesASession() {
            when(userRepository.findByIdentifier("9876543210")).thenReturn(Optional.of(user));

            LoginChallengeResponse challenge = authService.login(
                    new LoginRequest("9876543210", PASSWORD));

            assertThat(challenge.mfaToken()).isNotBlank();
            assertThat(challenge.enrollmentRequired()).isTrue();
            assertThat(challenge.expiresInSeconds()).isEqualTo(600);
            verifyNoInteractions(refreshTokenRepository);
        }

        @Test
        @DisplayName("CR-060: with MFA disabled a correct password returns a session directly, no challenge")
        void mfaDisabledSignsInDirectly() {
            doReturn(false).when(securityProperties).mfaRequired();
            when(userRepository.findByIdentifier("9876543210")).thenReturn(Optional.of(user));

            LoginChallengeResponse result = authService.login(
                    new LoginRequest("9876543210", PASSWORD));

            assertThat(result.isSignedIn()).isTrue();
            assertThat(result.session()).isNotNull();
            assertThat(result.session().accessToken()).isNotBlank();
            assertThat(result.session().refreshToken()).isNotBlank();
            assertThat(result.session().user().mobileNo()).isEqualTo("9876543210");
            // No half-finished challenge is handed out alongside a live session.
            assertThat(result.mfaToken()).isNull();
            assertThat(result.enrollmentRequired()).isFalse();

            // The audit trail must say what actually happened. Recording
            // LOGIN_MFA_REQUIRED here would claim a second factor was demanded
            // when none was.
            verify(auditService).success(eq(AuditAction.LOGIN_SUCCESS), eq(10L), any(), any(), any());
            verify(auditService, never()).success(eq(AuditAction.LOGIN_MFA_REQUIRED), any(), any(), any(), any());
        }

        @Test
        @DisplayName("CR-060: disabling MFA does not weaken the password check - a wrong password still fails")
        void mfaDisabledStillRejectsWrongPassword() {
            // The whole risk of a bypass flag is that it bypasses more than
            // intended. The first factor must be enforced exactly as before.
            doReturn(false).when(securityProperties).mfaRequired();
            when(userRepository.findByIdentifier("9876543210")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(new LoginRequest("9876543210", "Wrong@2026")))
                    .isInstanceOf(AuthException.class);
            verifyNoInteractions(refreshTokenRepository);
        }

        @Test
        @DisplayName("CR-060: a locked account is still refused when MFA is disabled")
        void mfaDisabledStillRefusesLockedAccount() {
            doReturn(false).when(securityProperties).mfaRequired();
            user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
            when(userRepository.findByIdentifier("9876543210")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(new LoginRequest("9876543210", PASSWORD)))
                    .isInstanceOf(AuthException.class);
            verifyNoInteractions(refreshTokenRepository);
        }

        @Test
        @DisplayName("succeeds with the mobile number once MFA is completed")
        void loginByMobile() {
            when(userRepository.findByIdentifier("9876543210")).thenReturn(Optional.of(user));

            LoginResponse response = signIn("9876543210");

            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresInSeconds()).isEqualTo(900);
            assertThat(response.user().mobileNo()).isEqualTo("9876543210");
        }

        @Test
        @DisplayName("succeeds with the email address")
        void loginByEmail() {
            when(userRepository.findByIdentifier("owner@sarahardware.in"))
                    .thenReturn(Optional.of(user));

            assertThat(signIn("owner@sarahardware.in").accessToken()).isNotBlank();
        }

        @Test
        @DisplayName("resets the failure counter and stamps last login")
        void successResetsCounters() {
            user.setFailedLoginAttempts(3);
            when(userRepository.findByIdentifier(anyString())).thenReturn(Optional.of(user));

            authService.login(new LoginRequest("9876543210", PASSWORD));

            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getLockedUntil()).isNull();
            assertThat(user.getLastLoginAt()).isNotNull();
        }

        @Test
        @DisplayName("an unknown account and a wrong password are indistinguishable")
        void enumerationProtection() {
            when(userRepository.findByIdentifier("0000000000")).thenReturn(Optional.empty());
            when(userRepository.findByIdentifier("9876543210")).thenReturn(Optional.of(user));

            AuthException unknown = catchAuth(() -> authService.login(
                    new LoginRequest("0000000000", "Whatever@1")));
            AuthException wrongPassword = catchAuth(() -> authService.login(
                    new LoginRequest("9876543210", "Whatever@1")));

            assertThat(unknown.getMessage()).isEqualTo(wrongPassword.getMessage());
            assertThat(unknown.getCode()).isEqualTo(wrongPassword.getCode());
            assertThat(unknown.getStatus()).isEqualTo(wrongPassword.getStatus());
        }

        @Test
        @DisplayName("an inactive account gives the same response as a wrong password")
        void inactiveIndistinguishable() {
            user.setStatus(UserStatus.INACTIVE);
            when(userRepository.findByIdentifier(anyString())).thenReturn(Optional.of(user));

            assertThat(catchAuth(() -> authService.login(
                    new LoginRequest("9876543210", PASSWORD))).getCode())
                    .isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        @DisplayName("a locked account gives the same response, revealing no lock state")
        void lockedIndistinguishable() {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
            when(userRepository.findByIdentifier(anyString())).thenReturn(Optional.of(user));

            AuthException ex = catchAuth(() -> authService.login(
                    new LoginRequest("9876543210", PASSWORD)));
            assertThat(ex.getMessage()).isEqualTo(AuthException.GENERIC_FAILURE);
        }

        @Test
        @DisplayName("the first four failures increment but do not lock")
        void failuresBeforeLock() {
            when(userRepository.findByIdentifier(anyString())).thenReturn(Optional.of(user));

            for (int attempt = 1; attempt <= 4; attempt++) {
                catchAuth(() -> authService.login(new LoginRequest("9876543210", "Wrong@1234")));
                assertThat(user.getFailedLoginAttempts()).isEqualTo(attempt);
                assertThat(user.isLocked()).isFalse();
            }
        }

        @Test
        @DisplayName("the fifth failure locks the account for 15 minutes")
        void fifthFailureLocks() {
            user.setFailedLoginAttempts(4);
            when(userRepository.findByIdentifier(anyString())).thenReturn(Optional.of(user));

            catchAuth(() -> authService.login(new LoginRequest("9876543210", "Wrong@1234")));

            assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
            assertThat(user.isLocked()).isTrue();
            assertThat(user.getLockedUntil())
                    .isAfter(LocalDateTime.now().plusMinutes(14))
                    .isBefore(LocalDateTime.now().plusMinutes(16));
            verify(auditService).failure(eq(AuditAction.ACCOUNT_LOCKED), eq(10L),
                    anyString(), anyString());
        }

        @Test
        @DisplayName("login works again once the lock has expired")
        void loginAfterLockExpiry() {
            user.setFailedLoginAttempts(5);
            user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
            when(userRepository.findByIdentifier(anyString())).thenReturn(Optional.of(user));

            assertThat(signIn("9876543210").accessToken()).isNotBlank();
            assertThat(user.getFailedLoginAttempts()).isZero();
        }

        @Test
        @DisplayName("only the token hash is persisted, never the raw token")
        void refreshTokenStoredHashed() {
            when(userRepository.findByIdentifier(anyString())).thenReturn(Optional.of(user));

            LoginResponse response = signIn("9876543210");

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());
            assertThat(captor.getValue().getTokenHash())
                    .hasSize(64)
                    .isNotEqualTo(response.refreshToken())
                    .isEqualTo(jwtService.hashToken(response.refreshToken()));
        }
    }

    // =============================================================
    @Nested
    @DisplayName("refresh")
    class Refresh {

        private RefreshToken storedFor(String raw) {
            return RefreshToken.builder()
                    .id(1L).user(user).tokenHash(jwtService.hashToken(raw))
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();
        }

        @Test
        @DisplayName("rotates: the old token is revoked and linked to its replacement")
        void rotation() {
            String raw = jwtService.generateRefreshToken();
            RefreshToken stored = storedFor(raw);
            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(stored));

            LoginResponse response = authService.refresh(raw);

            assertThat(response.refreshToken()).isNotEqualTo(raw);
            assertThat(stored.getRevokedAt()).isNotNull();
            assertThat(stored.getRevokedReason()).isEqualTo(RevokedReason.ROTATED);
            assertThat(stored.getReplacedByTokenId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("reuse of a rotated token revokes every session and bumps token_version")
        void reuseDetection() {
            String raw = jwtService.generateRefreshToken();
            RefreshToken alreadyRotated = storedFor(raw);
            alreadyRotated.revoke(RevokedReason.ROTATED);
            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(alreadyRotated));

            // BUG-AUTH-009's fix made the theft path re-read the user through
            // findById, because stored.getUser() is a lazy proxy that
            // revokeAllForUser's clearAutomatically detaches. This test was
            // never updated, so findById returned Mockito's default empty
            // Optional and the method threw INVALID_REFRESH_TOKEN from that
            // orElseThrow - before it could revoke anything. The assertions
            // below were therefore reporting a broken reuse response as a
            // stubbing gap. See BUG-AUTH-014.
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));

            assertThatThrownBy(() -> authService.refresh(raw))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("code", "TOKEN_REUSE");

            verify(refreshTokenRepository).revokeAllForUser(
                    eq(10L), eq(RevokedReason.REUSE_DETECTED), any(LocalDateTime.class));
            assertThat(user.getTokenVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("an expired token is refused")
        void expired() {
            String raw = jwtService.generateRefreshToken();
            RefreshToken stored = storedFor(raw);
            stored.setExpiresAt(LocalDateTime.now().minusDays(1));
            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(stored));

            assertThatThrownBy(() -> authService.refresh(raw))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("code", "REFRESH_TOKEN_EXPIRED");
        }

        @Test
        @DisplayName("an unknown token is refused")
        void unknown() {
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> authService.refresh("nonsense"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_REFRESH_TOKEN");
        }

        @Test
        @DisplayName("a deactivated user cannot refresh, even with a valid token")
        void deactivatedUser() {
            String raw = jwtService.generateRefreshToken();
            user.setStatus(UserStatus.INACTIVE);
            RefreshToken stored = storedFor(raw);
            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(stored));

            assertThatThrownBy(() -> authService.refresh(raw))
                    .isInstanceOf(AuthException.class);
        }
    }

    // =============================================================
    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("this device only: token_version is left alone")
        void logoutDoesNotBumpTokenVersion() {
            String raw = jwtService.generateRefreshToken();
            RefreshToken stored = RefreshToken.builder()
                    .id(1L).user(user).tokenHash(jwtService.hashToken(raw))
                    .expiresAt(LocalDateTime.now().plusDays(7)).build();
            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(stored));

            authService.logout(raw);

            assertThat(stored.getRevokedReason()).isEqualTo(RevokedReason.LOGOUT);
            // Closing the counter terminal must not sign the owner out on their phone.
            assertThat(user.getTokenVersion()).isZero();
            verify(refreshTokenRepository, never()).revokeAllForUser(
                    anyLong(), any(), any());
        }

        @Test
        @DisplayName("an unknown token is silently accepted, so a client can always clear state")
        void logoutIsIdempotent() {
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
            authService.logout("stale-token");
            authService.logout(null);
        }

        @Test
        @DisplayName("logout-all revokes everything and bumps token_version")
        void logoutAll() {
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));

            authService.logoutAllDevices(10L);

            verify(refreshTokenRepository).revokeAllForUser(
                    eq(10L), eq(RevokedReason.LOGOUT_ALL), any(LocalDateTime.class));
            assertThat(user.getTokenVersion()).isEqualTo(1);
        }
    }

    // =============================================================
    @Nested
    @DisplayName("password")
    class Password {

        @Test
        @DisplayName("change signs the user out everywhere")
        void changePassword() {
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));

            authService.changePassword(10L, new ChangePasswordRequest(PASSWORD, "Brand@New2026"));

            assertThat(passwordEncoder.matches("Brand@New2026", user.getPasswordHash())).isTrue();
            assertThat(user.getTokenVersion()).isEqualTo(1);
            assertThat(user.isMustChangePassword()).isFalse();
            verify(refreshTokenRepository).revokeAllForUser(
                    eq(10L), eq(RevokedReason.PASSWORD_CHANGED), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("a wrong current password is refused")
        void wrongCurrentPassword() {
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.changePassword(10L,
                    new ChangePasswordRequest("Nope@1234", "Brand@New2026")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "WRONG_PASSWORD");
        }

        @Test
        @DisplayName("reusing the same password is refused")
        void samePasswordRefused() {
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.changePassword(10L,
                    new ChangePasswordRequest(PASSWORD, PASSWORD)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("different");
        }

        @Test
        @DisplayName("forgot-password sends nothing for an unknown identifier")
        void forgotUnknown() {
            when(userRepository.findByIdentifier(anyString())).thenReturn(Optional.empty());

            authService.forgotPassword(new ForgotPasswordRequest("0000000000"));

            verifyNoInteractions(mailService);
            verify(resetTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("forgot-password stores only the hash and emails the raw token")
        void forgotStoresHashOnly() {
            when(userRepository.findByIdentifier(anyString())).thenReturn(Optional.of(user));
            when(resetTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            authService.forgotPassword(new ForgotPasswordRequest("9876543210"));

            ArgumentCaptor<PasswordResetToken> tokenCaptor =
                    ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(resetTokenRepository).save(tokenCaptor.capture());
            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            verify(mailService).sendPasswordResetLink(
                    eq("owner@sarahardware.in"), anyString(), urlCaptor.capture());

            assertThat(tokenCaptor.getValue().getTokenHash()).hasSize(64);
            assertThat(urlCaptor.getValue())
                    .doesNotContain(tokenCaptor.getValue().getTokenHash());
        }

        @Test
        @DisplayName("requesting a new link invalidates any outstanding one")
        void forgotInvalidatesPrevious() {
            when(userRepository.findByIdentifier(anyString())).thenReturn(Optional.of(user));
            when(resetTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            authService.forgotPassword(new ForgotPasswordRequest("9876543210"));

            verify(resetTokenRepository).invalidateAllForUser(eq(10L), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("a valid reset token sets the password, is marked used, and kills sessions")
        void resetSucceeds() {
            String raw = jwtService.generateRefreshToken();
            PasswordResetToken token = PasswordResetToken.builder()
                    .id(1L).user(user).tokenHash(jwtService.hashToken(raw))
                    .expiresAt(LocalDateTime.now().plusMinutes(20)).build();
            when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
            when(resetTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            authService.resetPassword(new ResetPasswordRequest(raw, "Reset@New2026"));

            assertThat(passwordEncoder.matches("Reset@New2026", user.getPasswordHash())).isTrue();
            assertThat(token.getUsedAt()).isNotNull();
            assertThat(user.getTokenVersion()).isEqualTo(1);
            verify(refreshTokenRepository).revokeAllForUser(
                    eq(10L), eq(RevokedReason.PASSWORD_RESET), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("an already-used reset token cannot be replayed")
        void resetTokenSingleUse() {
            String raw = jwtService.generateRefreshToken();
            PasswordResetToken used = PasswordResetToken.builder()
                    .id(1L).user(user).tokenHash(jwtService.hashToken(raw))
                    .expiresAt(LocalDateTime.now().plusMinutes(20))
                    .usedAt(LocalDateTime.now().minusMinutes(1)).build();
            when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(used));

            assertThatThrownBy(() -> authService.resetPassword(
                    new ResetPasswordRequest(raw, "Reset@New2026")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_RESET_TOKEN");
        }

        @Test
        @DisplayName("an expired reset token is refused")
        void resetTokenExpired() {
            String raw = jwtService.generateRefreshToken();
            PasswordResetToken expired = PasswordResetToken.builder()
                    .id(1L).user(user).tokenHash(jwtService.hashToken(raw))
                    .expiresAt(LocalDateTime.now().minusMinutes(1)).build();
            when(resetTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> authService.resetPassword(
                    new ResetPasswordRequest(raw, "Reset@New2026")))
                    .isInstanceOf(BusinessException.class);
        }
    }

    private AuthException catchAuth(Runnable action) {
        try {
            action.run();
        } catch (AuthException ex) {
            return ex;
        }
        throw new AssertionError("Expected AuthException");
    }
}
