package com.hardware.erp.platformadmin.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.security.totp.TotpSecretConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Hardware ERP staff, never a tenant. Carries no tenant_id and no repository
 * for this entity is ever filtered by one - see the V39 migration comment
 * for why this is a disjoint table rather than a flag on app_user.
 */
@Entity
@Table(name = "platform_admin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformAdmin extends BaseEntity {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final int LOCK_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "platform_admin_id")
    private Long id;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private PlatformAdminRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PlatformAdminStatus status = PlatformAdminStatus.ACTIVE;

    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private boolean mfaEnabled = false;

    /**
     * Base32 TOTP seed, encrypted at rest. May be populated before mfaEnabled
     * flips true - see the migration comment: a regenerated, unconfirmed
     * secret from an abandoned /mfa/enroll call.
     */
    @Convert(converter = TotpSecretConverter.class)
    @Column(name = "totp_secret", length = 255)
    private String totpSecret;

    @Column(name = "mfa_enrolled_at")
    private LocalDateTime mfaEnrolledAt;

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

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public boolean isActive() {
        return status == PlatformAdminStatus.ACTIVE;
    }

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

    public void invalidateAllTokens() {
        this.tokenVersion = (tokenVersion == null ? 0 : tokenVersion) + 1;
    }

    /** Stores a freshly generated, not-yet-confirmed secret. mfaEnabled stays false until confirmEnrollment(). */
    public void beginMfaEnrollment(String base32Secret) {
        this.totpSecret = base32Secret;
        this.mfaEnabled = false;
        this.mfaEnrolledAt = null;
    }

    public void confirmMfaEnrollment() {
        this.mfaEnabled = true;
        this.mfaEnrolledAt = LocalDateTime.now();
    }

    public Set<PlatformPermission> permissions() {
        return role.permissions();
    }
}
