package com.hardware.erp.subscription.entity;

import com.hardware.erp.tenant.entity.SubscriptionTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * CR-088. A sellable plan. tier is the locked V15 value the rest of the
 * application already gates on; planCode is the marketed name. Price is
 * data here, never a constant anywhere in Java or TypeScript.
 */
@Entity
@Table(name = "subscription_plan")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_plan_id")
    private Long id;

    @Column(name = "plan_code", nullable = false, length = 20)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 10)
    private SubscriptionTier tier;

    @Column(name = "plan_name", nullable = false, length = 50)
    private String planName;

    @Column(name = "tagline", nullable = false, length = 200)
    private String tagline;

    @Column(name = "price_paise", nullable = false)
    private Long pricePaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "billing_period", nullable = false, length = 20)
    private String billingPeriod;

    @Column(name = "recommended", nullable = false)
    private boolean recommended;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
