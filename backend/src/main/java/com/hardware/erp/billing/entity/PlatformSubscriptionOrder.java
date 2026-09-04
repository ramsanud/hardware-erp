package com.hardware.erp.billing.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

/**
 * One Razorpay Order created to move a tenant up a subscription tier.
 * Never mutates tenant.subscriptionTier itself - only a captured
 * PlatformSubscriptionPayment against this order does that, in
 * SubscriptionBillingService, so an abandoned checkout (order created,
 * never paid) has zero effect on the tenant's plan.
 */
@Entity
@Table(name = "platform_subscription_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSubscriptionOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "platform_subscription_order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_tier", nullable = false, length = 10)
    private SubscriptionTier requestedTier;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "razorpay_order_id", nullable = false, length = 64, unique = true)
    private String razorpayOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubscriptionOrderStatus status = SubscriptionOrderStatus.CREATED;
}
