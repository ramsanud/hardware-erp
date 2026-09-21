package com.hardware.erp.backup.entity;

import com.hardware.erp.platformadmin.entity.TenantExportFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * CR-092. One row per backup taken, with the snapshot itself so a past
 * backup can be downloaded again. The nightly job prunes each tenant to
 * its most recent seven so the table does not grow without bound.
 */
@Entity
@Table(name = "tenant_backup")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantBackup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tenant_backup_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 10)
    private TenantExportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private BackupTrigger triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackupStatus status;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** Lazy: the listing never needs the bytes, only the download does. */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "file_data")
    private byte[] fileData;

    @Column(name = "error_detail", length = 500)
    private String errorDetail;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum BackupTrigger { MANUAL, SCHEDULED }

    public enum BackupStatus { COMPLETED, FAILED }
}
