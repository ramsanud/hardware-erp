package com.hardware.erp.platformadmin.entity;

import com.hardware.erp.auth.entity.RevokedReason;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Mirrors auth.entity.RefreshToken exactly, on a disjoint table - see V39 migration comment. */
@Entity
@Table(name = "platform_admin_refresh_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformAdminRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "platform_admin_id", nullable = false)
    private PlatformAdmin admin;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 40)
    private RevokedReason revokedReason;

    @Column(name = "replaced_by_token_id")
    private Long replacedByTokenId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isUsable() {
        return !isRevoked() && !isExpired();
    }

    public void revoke(RevokedReason reason) {
        if (revokedAt == null) {
            this.revokedAt = LocalDateTime.now();
            this.revokedReason = reason;
        }
    }
}
