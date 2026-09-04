package com.hardware.erp.product.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A sellable catalogue item. Identification priority is barcode ->
 * manufacturer_code -> model_no -> product_code, never name alone
 * (FEATURE_REGISTRY Module 6).
 *
 * Carries its own current pricing for now - see Depends On below. Never
 * hard-deleted: Purchase, Inventory and Invoice will reference product_id
 * permanently once those modules exist.
 *
 * Depends On:
 *   Product Variant (not yet built) will add product_price_history and
 *   per-variant SKU/pricing for products that need more than one. Until
 *   then this entity's own price fields ARE the current price - changing
 *   them changes what the next sale charges, exactly like Supplier's
 *   creditLimitPaise. A sold invoice line will snapshot the price at sale
 *   time once Module 11 exists; this entity is not that snapshot.
 */
@Entity
@Table(name = "product")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "product_code", nullable = false, length = 30)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Column(name = "model_no", length = 60)
    private String modelNo;

    @Column(name = "manufacturer_code", length = 60)
    private String manufacturerCode;

    @Column(name = "barcode", length = 60)
    private String barcode;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "hsn_code", length = 10)
    private String hsnCode;

    /**
     * CR-053 backlog item 1. A secondary unit of measure, e.g. label="BOX",
     * conversionFactor=12.0000 meaning 1 BOX = 12 of this product's own
     * `unit`. Both null together for a product with no alternate unit -
     * never a zero factor, which would read as a real "12 PCS = 0 BOX".
     * Printed on the invoice PDF only when Tenant.showAlternateUnit is on.
     */
    @Column(name = "alt_unit_label", length = 30)
    private String altUnitLabel;

    @Column(name = "alt_unit_conversion_factor", precision = 18, scale = 4)
    private BigDecimal altUnitConversionFactor;

    @Column(name = "gst_rate_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstRatePercent = BigDecimal.ZERO;

    /** Paise, never a floating point rupee value - same reasoning as Supplier.creditLimitPaise. */
    @Column(name = "purchase_price_paise", nullable = false)
    @Builder.Default
    private Long purchasePricePaise = 0L;

    @Column(name = "selling_price_paise", nullable = false)
    @Builder.Default
    private Long sellingPricePaise = 0L;

    @Column(name = "mrp_paise", nullable = false)
    @Builder.Default
    private Long mrpPaise = 0L;

    /** Quantities are DECIMAL(18,4) (PROJECT_SKILLS #16) - a box may be sold as fractional cartons. */
    @Column(name = "minimum_stock", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal minimumStock = BigDecimal.ZERO;

    @Column(name = "reorder_level", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    public boolean isActive() {
        return status == ProductStatus.ACTIVE && deletedAt == null;
    }

    public void softDelete(Long actingUserId) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = actingUserId;
        this.status = ProductStatus.INACTIVE;
    }
}
