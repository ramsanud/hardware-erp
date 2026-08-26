package com.hardware.erp.tenant.entity;

import com.hardware.erp.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * CR-032 - a trial code the shop's OWNER redeems in Shop Settings to grant
 * their own tenant a subscription tier for a limited time (trialDays from
 * the moment of redemption), after which it reverts to FREE automatically
 * (checked lazily - see SubscriptionServiceImpl.currentTier()). Distinct
 * from Coupon (CR-028), which discounts a retail customer's invoice - this
 * is the shop's own plan, never a customer-facing discount. Tenant-scoped
 * like every coupon-shaped table here (CR-016): a code created in one shop
 * can never be redeemed by another.
 */
@Entity
@Table(name = "subscription_coupon")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionCoupon extends BaseEntity {

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
    @Column(name = "granted_tier", nullable = false, length = 10)
    private SubscriptionTier grantedTier;

    @Column(name = "trial_days", nullable = false)
    private Integer trialDays;

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
    private SubscriptionCouponStatus status = SubscriptionCouponStatus.ACTIVE;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    public boolean isCurrentlyRedeemable() {
        if (status != SubscriptionCouponStatus.ACTIVE) {
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
