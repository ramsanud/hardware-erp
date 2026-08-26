package com.hardware.erp.coupon.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A retail discount code (CR-028), tenant-scoped and optionally restricted
 * to specific products - an empty {@link #products} means it applies to
 * everything in the cart. Applied only at invoice/quotation creation time;
 * never changes a product's own price.
 */
@Entity
@Table(name = "coupon")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_purchase_paise")
    private Long minPurchasePaise;

    @Column(name = "max_discount_paise")
    private Long maxDiscountPaise;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "times_used", nullable = false)
    @Builder.Default
    private Integer timesUsed = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CouponStatus status = CouponStatus.ACTIVE;

    /** Empty = applies to every product. Populated = restricted to exactly these. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "coupon_product",
            joinColumns = @JoinColumn(name = "coupon_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"))
    @Builder.Default
    private Set<Product> products = new LinkedHashSet<>();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    public boolean isRestrictedToProducts() {
        return products != null && !products.isEmpty();
    }

    public boolean isCurrentlyValid() {
        if (status != CouponStatus.ACTIVE) {
            return false;
        }
        LocalDate today = LocalDate.now();
        if (validFrom != null && today.isBefore(validFrom)) {
            return false;
        }
        if (validUntil != null && today.isAfter(validUntil)) {
            return false;
        }
        return usageLimit == null || timesUsed < usageLimit;
    }
}
