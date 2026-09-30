package com.hardware.erp.subscription.service.impl;

import com.hardware.erp.subscription.entity.Feature;
import com.hardware.erp.subscription.entity.FeatureKey;
import com.hardware.erp.subscription.entity.SubscriptionPlan;
import com.hardware.erp.subscription.entity.SubscriptionStatus;
import com.hardware.erp.subscription.entity.TenantSubscription;
import com.hardware.erp.subscription.exception.FeatureNotAvailableException;
import com.hardware.erp.subscription.repository.FeatureRepository;
import com.hardware.erp.subscription.repository.PlanFeatureRepository;
import com.hardware.erp.subscription.repository.SubscriptionPlanRepository;
import com.hardware.erp.subscription.service.SubscriptionLifecycleService;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CR-088. The plan-feature matrix cache is exercised for real here (not
 * mocked away) - load()/matrix() is the part most likely to silently drop
 * a feature if a FeatureKey enum constant and a `feature` row ever drift.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeatureAccessServiceImplTest {

    @Mock private PlanFeatureRepository planFeatureRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private FeatureRepository featureRepository;
    @Mock private SubscriptionLifecycleService lifecycleService;

    @InjectMocks private FeatureAccessServiceImpl service;

    private SubscriptionPlan basic;
    private SubscriptionPlan pro;
    private SubscriptionPlan premium;

    @BeforeEach
    void setUp() {
        basic = plan(1L, "BASIC", SubscriptionTier.FREE, 0);
        pro = plan(2L, "PRO", SubscriptionTier.PRO, 1);
        premium = plan(3L, "PREMIUM", SubscriptionTier.MAX, 2);

        when(planFeatureRepository.matrix()).thenReturn(List.of(
                new Object[]{"BASIC", "PRODUCTS"},
                new Object[]{"BASIC", "GST_BILLING"},
                new Object[]{"PRO", "PRODUCTS"},
                new Object[]{"PRO", "GST_BILLING"},
                new Object[]{"PRO", "QUOTATION"},
                new Object[]{"PREMIUM", "PRODUCTS"},
                new Object[]{"PREMIUM", "GST_BILLING"},
                new Object[]{"PREMIUM", "QUOTATION"},
                new Object[]{"PREMIUM", "SMART_SUBSTITUTE"},
                // A row for a key this build's FeatureKey enum does not
                // recognise yet must be skipped, not blow up the cache load.
                new Object[]{"PREMIUM", "SOME_FUTURE_FEATURE"}
        ));
        when(planRepository.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(basic, pro, premium));
        when(planRepository.findByTier(SubscriptionTier.FREE)).thenReturn(Optional.of(basic));
        when(featureRepository.findByFeatureKey(any())).thenAnswer(inv -> {
            Feature f = new Feature();
            f.setFeatureKey(inv.getArgument(0));
            f.setFeatureName(inv.getArgument(0) + " (display name)");
            return Optional.of(f);
        });
    }

    private SubscriptionPlan plan(Long id, String code, SubscriptionTier tier, int order) {
        SubscriptionPlan p = new SubscriptionPlan();
        p.setId(id);
        p.setPlanCode(code);
        p.setTier(tier);
        p.setPlanName(code.charAt(0) + code.substring(1).toLowerCase());
        p.setDisplayOrder(order);
        p.setActive(true);
        return p;
    }

    private TenantSubscription subscriptionOn(SubscriptionPlan plan, SubscriptionStatus status) {
        return TenantSubscription.builder().id(1L).tenantId(10L).plan(plan).status(status)
                .startedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("a feature only PREMIUM carries is refused for a BASIC-effective tenant")
    void basicTenantLacksPremiumFeature() {
        when(lifecycleService.currentFor(10L)).thenReturn(subscriptionOn(basic, SubscriptionStatus.ACTIVE));

        assertThat(service.hasFeature(10L, FeatureKey.SMART_SUBSTITUTE)).isFalse();
        assertThat(service.hasFeature(10L, FeatureKey.PRODUCTS)).isTrue();
    }

    @Test
    @DisplayName("access() names the cheapest plan that carries a feature the tenant lacks")
    void accessNamesCheapestPlan() {
        when(lifecycleService.currentFor(10L)).thenReturn(subscriptionOn(basic, SubscriptionStatus.ACTIVE));

        var response = service.access(10L, FeatureKey.QUOTATION);

        assertThat(response.allowed()).isFalse();
        assertThat(response.currentPlanCode()).isEqualTo("BASIC");
        assertThat(response.requiredPlanCode()).isEqualTo("PRO");
    }

    @Test
    @DisplayName("cheapestPlanWith returns null when no active plan carries the feature")
    void cheapestPlanWithReturnsNullWhenNoneCarryIt() {
        assertThat(service.cheapestPlanWith(FeatureKey.MULTI_BRANCH)).isNull();
    }

    @Test
    @DisplayName("an EXPIRED subscription's effective plan falls back to Basic even though it is still stored on Premium")
    void expiredSubscriptionFallsBackToBasic() {
        TenantSubscription expired = subscriptionOn(premium, SubscriptionStatus.EXPIRED);
        when(lifecycleService.currentFor(10L)).thenReturn(expired);

        SubscriptionPlan effective = service.effectivePlan(10L);

        assertThat(effective.getPlanCode()).isEqualTo("BASIC");
        assertThat(service.hasFeature(10L, FeatureKey.SMART_SUBSTITUTE)).isFalse();
    }

    @Test
    @DisplayName("a TRIAL subscription on Premium keeps full Premium features")
    void trialGrantsFullFeatures() {
        when(lifecycleService.currentFor(10L)).thenReturn(subscriptionOn(premium, SubscriptionStatus.TRIAL));

        assertThat(service.hasFeature(10L, FeatureKey.SMART_SUBSTITUTE)).isTrue();
    }

    @Test
    @DisplayName("refresh() drops the cache so a changed matrix is re-read on next use")
    void refreshDropsCache() {
        when(lifecycleService.currentFor(10L)).thenReturn(subscriptionOn(basic, SubscriptionStatus.ACTIVE));
        assertThat(service.hasFeature(10L, FeatureKey.QUOTATION)).isFalse();

        when(planFeatureRepository.matrix()).thenReturn(List.<Object[]>of(new Object[]{"BASIC", "QUOTATION"}));
        service.refresh();

        assertThat(service.hasFeature(10L, FeatureKey.QUOTATION)).isTrue();
    }
}
