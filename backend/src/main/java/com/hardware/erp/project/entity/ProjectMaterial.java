package com.hardware.erp.project.entity;

import com.hardware.erp.product.entity.Product;
import com.hardware.erp.supplier.entity.Supplier;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One product consumed by a project. Supplier is deliberately nullable -
 * some old stock was never recorded with a supplier, and that must never
 * block adding it to a project (request §10). unit/unit_price are
 * snapshotted at add-time, matching invoice_item's "never a live lookup"
 * rule - a later master-price change must not rewrite project history.
 */
@Entity
@Table(name = "project_material")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_material_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Optional - see class comment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "quantity_required", precision = 18, scale = 4)
    private BigDecimal quantityRequired;

    @Column(name = "quantity_estimated", precision = 18, scale = 4)
    private BigDecimal quantityEstimated;

    @Column(name = "quantity_actual", precision = 18, scale = 4)
    private BigDecimal quantityActual;

    @Column(name = "quantity_wastage", precision = 18, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal quantityWastage = BigDecimal.ZERO;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_price_paise", nullable = false)
    private Long unitPricePaise;

    @Column(name = "total_cost_paise", nullable = false)
    private Long totalCostPaise;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
