package com.hardware.erp.document.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * CR-101. A background render request for a report/document too heavy to
 * build on the HTTP request thread - see V62 for the table and why the
 * finished file lives in-row as BYTEA rather than an external object store.
 *
 * Not a {@code BaseEntity}: this row's own status timestamps
 * (started_at/completed_at) already say what BaseEntity's created_at/
 * updated_at would, and created_by is requested_by here, nullable because a
 * scheduled retention sweep is the only writer that ever touches a row
 * without a signed-in caller.
 */
@Entity
@Table(name = "report_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_job_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "requested_by")
    private Long requestedBy;

    /** e.g. "PARTY_STATEMENT", "DAY_BOOK", "GSTR1" - the same code the params were built from. */
    @Column(name = "report_type", nullable = false, length = 40)
    private String reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 10)
    private ReportJobFormat format;

    /** The report's own filters (from/to, partyId, period...), as JSON - see ReportJobParams. */
    @Column(name = "params_json", nullable = false)
    private String paramsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportJobStatus status;

    @Column(name = "file_name", length = 150)
    private String fileName;

    @Column(name = "file_data")
    private byte[] fileData;

    @Column(name = "file_size_bytes")
    private Integer fileSizeBytes;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = ReportJobStatus.PENDING;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
