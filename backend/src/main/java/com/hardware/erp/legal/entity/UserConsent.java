package com.hardware.erp.legal.entity;

import com.hardware.erp.auth.entity.User;
import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One recorded consent decision (CR-040).
 *
 * Append-only: withdrawing marketing consent, or accepting a newer Terms
 * version, inserts a new row. Nothing here is ever updated, because the point
 * of the record is that the earlier position remains provable.
 */
@Entity
@Table(name = "user_consent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserConsent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_consent_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 20)
    private ConsentType consentType;

    /** Null for MARKETING, which is a preference rather than a published document. */
    @Column(name = "document_version", length = 20)
    private String documentVersion;

    @Column(name = "granted", nullable = false)
    private boolean granted;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}
