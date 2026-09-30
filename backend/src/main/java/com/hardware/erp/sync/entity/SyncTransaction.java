package com.hardware.erp.sync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CR-091 Phase 9. One row per client-generated offline transaction.
 *
 * UNIQUE (tenant_id, client_uuid) is what makes a replay safe: uploading
 * the same UUID twice finds this row and the server returns its ALREADY
 * STORED outcome rather than creating a second invoice, stock movement or
 * ledger entry - the brief's own "if the same offline transaction is
 * uploaded twice, it must not create duplicate financial transactions."
 */
@Entity
@Table(name = "sync_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sync_transaction_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "client_uuid", nullable = false)
    private UUID clientUuid;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private SyncTransactionType transactionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SyncTransactionStatus status;

    @Column(name = "result_reference_type", length = 30)
    private String resultReferenceType;

    @Column(name = "result_reference_id")
    private Long resultReferenceId;

    @Column(name = "result_reference_number", length = 40)
    private String resultReferenceNumber;

    @Column(name = "conflict_reason", length = 500)
    private String conflictReason;

    @Column(name = "client_created_at", nullable = false)
    private LocalDateTime clientCreatedAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 1;

    @Column(name = "created_by")
    private Long createdBy;
}
