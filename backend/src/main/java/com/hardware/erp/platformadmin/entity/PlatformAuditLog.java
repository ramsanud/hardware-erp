package com.hardware.erp.platformadmin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** The platform-wide equivalent of security_audit_log - deliberately a disjoint table, see V39 migration comment. */
@Entity
@Table(name = "platform_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "platform_audit_log_id")
    private Long id;

    /**
     * Deliberately a plain id, not a @ManyToOne - see the V39 migration
     * comment: this column is not a foreign key, exactly like
     * security_audit_log.user_id, so a REQUIRES_NEW audit write for a
     * "created" event can never be rejected by the still-uncommitted row it
     * is describing. Null for an event with no resolvable account, e.g. a
     * login attempt against an unknown email.
     */
    @Column(name = "platform_admin_id")
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60)
    private PlatformAuditAction action;

    @Column(name = "target_type", length = 60)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
