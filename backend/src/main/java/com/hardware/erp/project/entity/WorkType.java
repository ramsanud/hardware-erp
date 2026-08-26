package com.hardware.erp.project.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

/**
 * A category of project work (Modular Kitchen, Stainless Steel Work, ...).
 * Deliberately a plain user-extensible table, not a Java enum - "add work
 * type" in the UI is a normal insert here, so a shop's own vocabulary is
 * never restricted to what shipped in the seed.
 */
@Entity
@Table(name = "work_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "work_type_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;
}
