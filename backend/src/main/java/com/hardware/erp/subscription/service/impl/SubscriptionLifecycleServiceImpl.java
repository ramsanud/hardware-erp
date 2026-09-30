package com.hardware.erp.subscription.service.impl;

import com.hardware.erp.billing.service.RazorpayConfigResolver;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.subscription.config.SubscriptionProperties;
import com.hardware.erp.subscription.dto.CurrentSubscriptionResponse;
import com.hardware.erp.subscription.dto.SubscriptionHistoryResponse;
import com.hardware.erp.subscription.dto.SubscriptionPlanResponse;
import com.hardware.erp.subscription.dto.UsageLimitResponse;
import com.hardware.erp.subscription.entity.FeatureKey;
import com.hardware.erp.subscription.entity.PlanUsageLimit;
import com.hardware.erp.subscription.entity.SubscriptionHistory;
import com.hardware.erp.subscription.entity.SubscriptionPlan;
import com.hardware.erp.subscription.entity.SubscriptionStatus;
import com.hardware.erp.subscription.entity.TenantSubscription;
import com.hardware.erp.subscription.repository.PlanUsageLimitRepository;
import com.hardware.erp.subscription.repository.SubscriptionHistoryRepository;
import com.hardware.erp.subscription.repository.SubscriptionPlanRepository;
import com.hardware.erp.subscription.repository.TenantSubscriptionRepository;
import com.hardware.erp.subscription.service.FeatureAccessService;
import com.hardware.erp.subscription.service.SubscriptionLifecycleService;
import com.hardware.erp.subscription.service.UsageTrackingService;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CR-088. Invariant kept here and nowhere else: tenant.subscription_tier
 * is the tier the shop may USE right now - the plan's tier while the
 * subscription grants paid features (TRIAL / ACTIVE / PAST_DUE), FREE
 * (=BASIC) otherwise. tenant_subscription.plan is the plan the shop is
 * subscribed to, which may still read PREMIUM while EXPIRED so the page
 * can say "your Premium plan expired on ...". Every transition writes a
 * subscription_history row and an activity_log entry.
 */
@Service
public class SubscriptionLifecycleServiceImpl implements SubscriptionLifecycleService {

    private static final String MODULE = "SUBSCRIPTION";
    private static final String ENTITY = "SUBSCRIPTION";

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository planRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final SubscriptionHistoryRepository historyRepository;
    private final PlanUsageLimitRepository usageLimitRepository;
    private final RazorpayConfigResolver razorpayConfigResolver;
    private final SubscriptionProperties properties;
    private final ActivityLogService activityLog;
    private final FeatureAccessService featureAccessService;
    private final UsageTrackingService usageTrackingService;

    public SubscriptionLifecycleServiceImpl(TenantRepository tenantRepository,
                                            SubscriptionPlanRepository planRepository,
                                            TenantSubscriptionRepository subscriptionRepository,
                                            SubscriptionHistoryRepository historyRepository,
                                            PlanUsageLimitRepository usageLimitRepository,
                                            RazorpayConfigResolver razorpayConfigResolver,
                                            SubscriptionProperties properties,
                                            ActivityLogService activityLog,
                                            // FeatureAccessServiceImpl needs currentFor() from this
                                            // class; the reverse edge is only used by current(), so
                                            // it is resolved lazily to break the constructor cycle.
                                            @Lazy FeatureAccessService featureAccessService,
                                            @Lazy UsageTrackingService usageTrackingService) {
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.historyRepository = historyRepository;
        this.usageLimitRepository = usageLimitRepository;
        this.razorpayConfigResolver = razorpayConfigResolver;
        this.properties = properties;
        this.activityLog = activityLog;
        this.featureAccessService = featureAccessService;
        this.usageTrackingService = usageTrackingService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> plans() {
        return planRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Override
    @Transactional
    public CurrentSubscriptionResponse current() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return toCurrentResponse(currentFor(tenantId));
    }

    @Override
    @Transactional
    public CurrentSubscriptionResponse changePlan(String planCode, String reason) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        SubscriptionPlan target = planRepository.findByPlanCode(planCode)
                .filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planCode));
        TenantSubscription current = currentFor(tenantId);

        boolean upgrade = target.getTier().ordinal() > effectiveTier(current).ordinal();
        if (upgrade && razorpayConfigResolver.resolve().active()) {
            // Same rule as the CR-057 settings picker: once a gateway is
            // configured, a paid tier is only ever granted by a verified
            // payment - never by a self-service POST.
            throw new BusinessException(
                    "Upgrading to " + target.getPlanName() + " needs checkout - use Upgrade on the plans page.",
                    HttpStatus.UNPROCESSABLE_ENTITY, "UPGRADE_REQUIRES_CHECKOUT");
        }
        if (current.getPlan().getId().equals(target.getId())
                && current.getStatus().grantsPaidFeatures()) {
            throw new BusinessException("Your shop is already on the " + target.getPlanName() + " plan.");
        }
        String why = reason == null || reason.isBlank()
                ? (upgrade ? "Self-service upgrade" : "Self-service downgrade") : reason.trim();
        TenantSubscription changed = applyTier(tenantId, target.getTier(), SubscriptionStatus.ACTIVE, null, why, null);
        return toCurrentResponse(changed);
    }

    @Override
    @Transactional
    public CurrentSubscriptionResponse cancel(String reason) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        TenantSubscription current = currentFor(tenantId);
        if (current.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new BusinessException("This subscription is already cancelled.");
        }
        if (current.getPlan().getTier() == SubscriptionTier.FREE
                && current.getStatus().grantsPaidFeatures()) {
            throw new BusinessException("The Basic plan is the floor - there is nothing to cancel. "
                    + "Your data stays available on Basic.");
        }
        transition(current, current.getPlan(), SubscriptionStatus.CANCELLED, null, reason, null);
        current.setCancelledAt(LocalDateTime.now());
        current.setCancellationReason(reason);
        subscriptionRepository.save(current);
        return toCurrentResponse(current);
    }

    /**
     * The sanctioned entry point for every caller that changes a tenant's
     * plan - Razorpay verification, coupon redemption, the settings picker,
     * registration (both the trial and the explicit-tier path) and this
     * class's own changePlan()/cancel(). Plain @Transactional (REQUIRED),
     * not REQUIRES_NEW: every one of those callers already owns a writable
     * transaction of its own, and a brand-new tenant's very first
     * subscription row in particular must be written in the SAME
     * transaction that inserts the tenant - a suspended REQUIRES_NEW
     * transaction cannot see that tenant row until it commits, and a
     * REQUIRES_NEW call from an EXISTING caller's still-open transaction
     * risks blocking on the row lock that transaction already holds. Only
     * currentFor()'s lazy expiry check below needs REQUIRES_NEW, because it
     * alone is reached from callers that hold no writable transaction
     * (readOnly entitlement/feature checks) - exactly CR-032's precedent.
     */
    @Override
    @Transactional
    public TenantSubscription applyTier(Long tenantId, SubscriptionTier tier, SubscriptionStatus status,
                                        LocalDateTime trialEndsAt, String reason, String paymentReference) {
        SubscriptionPlan plan = planRepository.findByTier(tier)
                .orElseThrow(() -> new IllegalStateException("No plan maps to tier " + tier + " - V57 not applied"));
        TenantSubscription subscription = findOrCreate(tenantId);
        transition(subscription, plan, status, trialEndsAt, reason, paymentReference);
        return subscription;
    }

    /** REQUIRED, same as applyTier() - joins registration's already-open transaction, never suspends it. */
    @Override
    @Transactional
    public TenantSubscription startForNewTenant(Long tenantId) {
        if (properties.trialEnabled()) {
            LocalDateTime trialEnd = LocalDateTime.now().plusDays(properties.trialDays());
            return applyTier(tenantId, properties.trialTier(), SubscriptionStatus.TRIAL, trialEnd,
                    properties.trialDays() + "-day trial on registration", null);
        }
        return applyTier(tenantId, SubscriptionTier.FREE, SubscriptionStatus.ACTIVE, null,
                "Registered on Basic", null);
    }

    @Override
    @Transactional
    public TenantSubscription startForNewTenant(Long tenantId, SubscriptionTier explicitTier) {
        return applyTier(tenantId, explicitTier, SubscriptionStatus.ACTIVE, null,
                "Chosen at registration", null);
    }

    /**
     * REQUIRES_NEW for the same reason as SubscriptionServiceImpl.currentTier()
     * (CR-032): the lazy expiry write below must flush even when the caller
     * is a readOnly transaction such as an entitlement check.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TenantSubscription currentFor(Long tenantId) {
        TenantSubscription subscription = findOrCreate(tenantId);
        LocalDateTime now = LocalDateTime.now();

        if (subscription.getStatus() == SubscriptionStatus.TRIAL
                && subscription.getTrialEndsAt() != null && now.isAfter(subscription.getTrialEndsAt())) {
            SubscriptionPlan basic = planRepository.findByTier(SubscriptionTier.FREE).orElseThrow();
            transition(subscription, basic, SubscriptionStatus.ACTIVE, null,
                    "Trial ended - continuing on Basic", null);
        } else if (subscription.getStatus() == SubscriptionStatus.ACTIVE
                && subscription.getEndsAt() != null && now.isAfter(subscription.getEndsAt())) {
            transition(subscription, subscription.getPlan(), SubscriptionStatus.PAST_DUE, null,
                    "Paid period ended - " + properties.pastDueGraceDays() + " day grace", null);
        } else if (subscription.getStatus() == SubscriptionStatus.PAST_DUE
                && subscription.getEndsAt() != null
                && now.isAfter(subscription.getEndsAt().plusDays(properties.pastDueGraceDays()))) {
            transition(subscription, subscription.getPlan(), SubscriptionStatus.EXPIRED, null,
                    "Grace period ended without payment", null);
        }
        return subscription;
    }

    // ---------------------------------------------------------------

    private TenantSubscription findOrCreate(Long tenantId) {
        return subscriptionRepository.findByTenantId(tenantId).orElseGet(() -> {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
            SubscriptionPlan plan = planRepository.findByTier(tenant.getSubscriptionTier()).orElseThrow();
            boolean trial = tenant.getSubscriptionTrialExpiresAt() != null;
            TenantSubscription created = TenantSubscription.builder()
                    .tenantId(tenantId)
                    .plan(plan)
                    .status(trial ? SubscriptionStatus.TRIAL : SubscriptionStatus.ACTIVE)
                    .trialEndsAt(tenant.getSubscriptionTrialExpiresAt())
                    .startedAt(tenant.getCreatedAt() == null ? LocalDateTime.now() : tenant.getCreatedAt())
                    .build();
            return subscriptionRepository.save(created);
        });
    }

    /** The single writer of plan/status/tier. */
    private void transition(TenantSubscription subscription, SubscriptionPlan plan, SubscriptionStatus status,
                            LocalDateTime trialEndsAt, String reason, String paymentReference) {
        String fromPlan = subscription.getPlan() == null ? null : subscription.getPlan().getPlanCode();
        String fromStatus = subscription.getStatus() == null ? null : subscription.getStatus().name();
        LocalDateTime now = LocalDateTime.now();

        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setTrialEndsAt(status == SubscriptionStatus.TRIAL ? trialEndsAt : null);
        if (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIAL) {
            subscription.setStartedAt(now);
            subscription.setCancelledAt(null);
            subscription.setCancellationReason(null);
            // Informational until a recurring-billing engine exists (CR-057
            // deliberately sells one period at a time): nothing expires a
            // self-declared or one-off-paid plan, so endsAt stays null.
            subscription.setRenewalAt(plan.getTier() == SubscriptionTier.FREE ? null : now.plusMonths(1));
        }
        if (paymentReference != null) {
            subscription.setPaymentReference(paymentReference);
            subscription.setPaymentStatus("PAID");
        }
        subscriptionRepository.save(subscription);

        Tenant tenant = tenantRepository.findById(subscription.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", subscription.getTenantId()));
        SubscriptionTier usableTier = status.grantsPaidFeatures() ? plan.getTier() : SubscriptionTier.FREE;
        tenant.setSubscriptionTier(usableTier);
        tenant.setSubscriptionTrialExpiresAt(status == SubscriptionStatus.TRIAL ? trialEndsAt : null);
        tenantRepository.save(tenant);

        historyRepository.save(SubscriptionHistory.builder()
                .tenantId(subscription.getTenantId())
                .fromPlanCode(fromPlan)
                .toPlanCode(plan.getPlanCode())
                .fromStatus(fromStatus)
                .toStatus(status.name())
                .reason(reason)
                .changedBy(SecurityUtils.currentUserId().orElse(null))
                .createdAt(now)
                .build());

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("planCode", fromPlan);
        before.put("status", fromStatus);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("planCode", plan.getPlanCode());
        after.put("status", status.name());
        after.put("reason", reason);
        activityLog.updated(MODULE, ENTITY, subscription.getId(), plan.getPlanName(), before, after);
    }

    private SubscriptionTier effectiveTier(TenantSubscription subscription) {
        return subscription.getStatus().grantsPaidFeatures() ? subscription.getPlan().getTier() : SubscriptionTier.FREE;
    }

    private SubscriptionPlanResponse toPlanResponse(SubscriptionPlan plan) {
        List<String> featureKeys = featureAccessService.featuresOf(plan.getPlanCode()).stream()
                .sorted(Comparator.comparingInt(FeatureKey::ordinal))
                .map(FeatureKey::name)
                .toList();
        List<UsageLimitResponse> limits = usageLimitRepository.findByPlanId(plan.getId()).stream()
                .sorted(Comparator.comparingInt(limit -> limit.getUsageKey().ordinal()))
                .map(limit -> new UsageLimitResponse(limit.getUsageKey().name(),
                        UsageTrackingServiceImpl.label(limit.getUsageKey()), limit.getIncludedCount()))
                .toList();
        return new SubscriptionPlanResponse(plan.getPlanCode(), plan.getTier().name(), plan.getPlanName(),
                plan.getTagline(), plan.getPricePaise(), IndianCurrencyFormat.rupees(plan.getPricePaise()),
                plan.getCurrency(), plan.getBillingPeriod(), plan.isRecommended(), plan.getDisplayOrder(),
                featureKeys, limits);
    }

    private CurrentSubscriptionResponse toCurrentResponse(TenantSubscription subscription) {
        SubscriptionPlan effective = featureAccessService.effectivePlan(subscription.getTenantId());
        List<String> featureKeys = featureAccessService.featuresOf(effective.getPlanCode()).stream()
                .sorted(Comparator.comparingInt(FeatureKey::ordinal))
                .map(FeatureKey::name)
                .toList();
        List<SubscriptionHistoryResponse> history = historyRepository
                .findTop20ByTenantIdOrderByCreatedAtDesc(subscription.getTenantId()).stream()
                .map(h -> new SubscriptionHistoryResponse(h.getFromPlanCode(), h.getToPlanCode(),
                        h.getFromStatus(), h.getToStatus(), h.getReason(), h.getCreatedAt()))
                .toList();
        SubscriptionPlan plan = subscription.getPlan();
        return new CurrentSubscriptionResponse(
                plan.getPlanCode(), plan.getPlanName(), plan.getTier().name(), subscription.getStatus().name(),
                effective.getPlanCode(), effective.getPlanName(),
                subscription.getTrialEndsAt(), subscription.getStartedAt(), subscription.getEndsAt(),
                subscription.getRenewalAt(), subscription.getCancelledAt(), subscription.getPaymentStatus(),
                razorpayConfigResolver.resolve().active(),
                featureKeys,
                usageTrackingService.usage(subscription.getTenantId()),
                history);
    }
}
