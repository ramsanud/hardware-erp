package com.hardware.erp.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CR-084. How many of a tenant's products were at or below their reorder
 * level on one calendar day. One row per (tenant, day), written by
 * {@link com.hardware.erp.inventory.job.LowStockSnapshotJob} and, on a
 * shop's first read of the trend, taken lazily for today.
 *
 * Not a {@code BaseEntity}: this row is written by a scheduled job with no
 * signed-in user, so created_by / updated_* would only ever be null, and V56
 * does not carry them. Hibernate validates the entity against the table, so
 * the two must agree exactly.
 */
@Entity
@Table(name = "low_stock_snapshot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LowStockSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "low_stock_snapshot_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "taken_on", nullable = false)
    private LocalDate takenOn;

    @Column(name = "low_stock_count", nullable = false)
    private int lowStockCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
