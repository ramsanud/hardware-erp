package com.hardware.erp.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A permission is a database row, not a Java constant.
 *
 * Modules 2-12 insert their own permissions through their own Flyway migration.
 * Nothing in this class has to change when Product or Invoice ships, which is
 * the whole point of the previous design being replaced.
 */
@Entity
@Table(name = "permission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Long id;

    @Column(name = "permission_code", nullable = false, length = 60, unique = true)
    private String code;

    @Column(name = "permission_name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    /** AUTH, CUSTOMER, SUPPLIER, PRODUCT, PURCHASE, SALES, INVENTORY, PAYMENT, EXPENSE, REPORT, SETTINGS */
    @Column(name = "module_code", nullable = false, length = 30)
    private String moduleCode;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Identity is the immutable business key, not the surrogate id, so a
     * Permission behaves correctly inside the Set on Role even before flush.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Permission that)) return false;
        return code != null && code.equals(that.code);
    }

    @Override
    public int hashCode() {
        return code == null ? 0 : code.hashCode();
    }
}
