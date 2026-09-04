package com.hardware.erp.auth.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.security.totp.TotpSecretConverter;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * Table is app_user: USER is a reserved word in PostgreSQL (and was in MySQL).
 *
 * Never hard-deleted. Every ERP document written from Module 2 onwards carries
 * created_by, and those references must still resolve to a name years later.
 */
@Entity
@Table(name = "app_user")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final int LOCK_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    /**
     * EAGER for the same reason as role: the security filter resolves it on
     * every request via AppUserDetails, and a lazy proxy there is exactly the
     * LazyInitializationException role already avoids (CR-016).
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "employee_code", length = 30)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "mobile_no", nullable = false, length = 15, unique = true)
    private String mobileNo;

    @Column(name = "email", length = 255, unique = true)
    private String email;

    /** BCrypt strength 12. The plain password never outlives the request thread. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /**
     * Carried in every access token as the "tv" claim and re-checked against
     * this column on every request. Incrementing it invalidates every
     * outstanding access token for this user at once, with no blacklist.
     */
    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private Integer tokenVersion = 0;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    /** CR-058 - mandatory MFA. Mirrors PlatformAdmin's own mfa_enabled/totp_secret/mfa_enrolled_at exactly. */
    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private boolean mfaEnabled = false;

    /**
     * Base32 TOTP seed, encrypted at rest. May be populated before mfaEnabled
     * flips true - a regenerated, unconfirmed secret from an abandoned
     * /mfa/enroll call.
     */
    @Convert(converter = TotpSecretConverter.class)
    @Column(name = "totp_secret", length = 255)
    private String totpSecret;

    @Column(name = "mfa_enrolled_at")
    private LocalDateTime mfaEnrolledAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    // ---------------------------------------------------------------
    // Domain behaviour. Kept on the entity so every caller applies the
    // same rules; a service that hand-rolls lockout will get it wrong.
    // ---------------------------------------------------------------

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE && deletedAt == null;
    }

    /** True once the caller should stop trying. */
    public boolean registerFailedLogin() {
        failedLoginAttempts = (failedLoginAttempts == null ? 0 : failedLoginAttempts) + 1;
        if (failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = LocalDateTime.now().plusMinutes(LOCK_MINUTES);
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = LocalDateTime.now();
    }

    /**
     * Every path that changes a password funnels through here, so none can
     * forget to bump tokenVersion and leave old sessions alive.
     */
    public void applyNewPassword(String encodedPassword, boolean forceChangeNextLogin) {
        this.passwordHash = encodedPassword;
        this.passwordChangedAt = LocalDateTime.now();
        this.mustChangePassword = forceChangeNextLogin;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        invalidateAllTokens();
    }

    public void invalidateAllTokens() {
        this.tokenVersion = (tokenVersion == null ? 0 : tokenVersion) + 1;
    }

    /** CR-058. Mirrors PlatformAdmin.beginMfaEnrollment exactly. */
    public void beginMfaEnrollment(String base32Secret) {
        this.totpSecret = base32Secret;
        this.mfaEnabled = false;
        this.mfaEnrolledAt = null;
    }

    public void confirmMfaEnrollment() {
        this.mfaEnabled = true;
        this.mfaEnrolledAt = LocalDateTime.now();
    }

    public void softDelete(Long actingUserId) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = actingUserId;
        this.status = UserStatus.INACTIVE;
        invalidateAllTokens();
    }

    public boolean isOwner() {
        return role != null && "OWNER".equals(role.getCode());
    }
}
