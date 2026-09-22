package com.hardware.erp.subscription.service.impl;

import com.hardware.erp.billing.config.EffectiveRazorpayConfig;
import com.hardware.erp.billing.service.RazorpayConfigResolver;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.subscription.config.SubscriptionProperties;
import com.hardware.erp.subscription.dto.UsageResponse;
import com.hardware.erp.subscription.entity.SubscriptionHistory;
import com.hardware.erp.subscription.entity.SubscriptionPlan;
import com.hardware.erp.subscription.entity.SubscriptionStatus;
import com.hardware.erp.subscription.entity.TenantSubscription;
import com.hardware.erp.subscription.repository.PlanUsageLimitRepository;
import com.hardware.erp.subscription.repository.SubscriptionHistoryRepository;
import com.hardware.erp.subscription.repository.SubscriptionPlanRepository;
import com.hardware.erp.subscription.repository.TenantSubscriptionRepository;
import com.hardware.erp.subscription.service.FeatureAccessService;
import com.hardware.erp.subscription.service.UsageTrackingService;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionLifecycleServiceImplTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private TenantSubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionHistoryRepository historyRepository;
    @Mock private PlanUsageLimitRepository usageLimitRepository;
    @Mock private RazorpayConfigResolver razorpayConfigResolver;
    @Mock private ActivityLogService activityLog;
    @Mock private FeatureAccessService featureAccessService;
    @Mock private UsageTrackingService usageTrackingService;

    private SubscriptionLifecycleServiceImpl service;

    private Tenant tenant;
    private SubscriptionPlan basic;
    private SubscriptionPlan pro;
    private SubscriptionPlan premium;

    @BeforeEach
    void setUp() {
        service = new SubscriptionLifecycleServiceImpl(tenantRepository, planRepository, subscriptionRepository,
                historyRepository, usageLimitRepository, razorpayConfigResolver,
                new SubscriptionProperties(14, SubscriptionTier.MAX, 7), activityLog,
                featureAccessService, usageTrackingService);

        tenant = Tenant.builder().id(10L).slug("t").name("T").status(TenantStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.FREE).build();
        tenant.setCreatedAt(LocalDateTime.now());
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        basic = plan(1L, "BASIC", SubscriptionTier.FREE);
        pro = plan(2L, "PRO", SubscriptionTier.PRO);
        premium = plan(3L, "PREMIUM", SubscriptionTier.MAX);
        when(planRepository.findByTier(SubscriptionTier.FREE)).thenReturn(Optional.of(basic));
        when(planRepository.findByTier(SubscriptionTier.PRO)).thenReturn(Optional.of(pro));
        when(planRepository.findByTier(SubscriptionTier.MAX)).thenReturn(Optional.of(premium));
        when(planRepository.findByPlanCode("BASIC")).thenReturn(Optional.of(basic));
        when(planRepository.findByPlanCode("PRO")).thenReturn(Optional.of(pro));
        when(planRepository.findByPlanCode("PREMIUM")).thenReturn(Optional.of(premium));

        when(subscriptionRepository.save(any(TenantSubscription.class))).thenAnswer(inv -> {
            TenantSubscription s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(100L);
            }
            return s;
        });
        when(historyRepository.save(any(SubscriptionHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(razorpayConfigResolver.resolve()).thenReturn(
                new EffectiveRazorpayConfig(false, "", "", false, "", "https://api.razorpay.com/v1", 0, 0));
        when(featureAccessService.effectivePlan(anyLong())).thenReturn(basic);
        when(usageTrackingService.usage(anyLong())).thenReturn(
                new UsageResponse(null, null, "BASIC", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SubscriptionPlan plan(Long id, String code, SubscriptionTier tier) {
        SubscriptionPlan p = new SubscriptionPlan();
        p.setId(id);
        p.setPlanCode(code);
        p.setPlanName(code.charAt(0) + code.substring(1).toLowerCase());
        p.setTier(tier);
        p.setActive(true);
        return p;
    }

    private void authenticateAsTenant(Long tenantId) {
        var role = com.hardware.erp.auth.entity.Role.builder().id(1L).code("OWNER").name("Owner")
                .systemRole(true).status(com.hardware.erp.auth.entity.RoleStatus.ACTIVE)
                .permissions(new java.util.LinkedHashSet<>()).build();
        var t = Tenant.builder().id(tenantId).slug("t").name("T").status(TenantStatus.ACTIVE).build();
        var user = com.hardware.erp.auth.entity.User.builder().id(1L).tenant(t).role(role)
                .fullName("Owner").mobileNo("9999999999").passwordHash("h")
                .status(com.hardware.erp.auth.entity.UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(user), null, List.of()));
    }

    @Test
    @DisplayName("startForNewTenant grants the configured trial when trials are enabled")
    void startForNewTenantGrantsTrial() {
        TenantSubscription subscription = service.startForNewTenant(10L);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);
        assertThat(subscription.getPlan().getTier()).isEqualTo(SubscriptionTier.MAX);
        assertThat(subscription.getTrialEndsAt()).isAfter(LocalDateTime.now().plusDays(13));
        assertThat(tenant.getSubscriptionTier()).isEqualTo(SubscriptionTier.MAX);
    }

    @Test
    @DisplayName("startForNewTenant with an explicit tier is ACTIVE immediately, no trial")
    void startForNewTenantWithExplicitTier() {
        TenantSubscription subscription = service.startForNewTenant(10L, SubscriptionTier.PRO);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getTrialEndsAt()).isNull();
        assertThat(tenant.getSubscriptionTier()).isEqualTo(SubscriptionTier.PRO);
    }

    @Test
    @DisplayName("an upgrade is refused with UPGRADE_REQUIRES_CHECKOUT once a payment gateway is active")
    void upgradeRefusedWhenGatewayActive() {
        service.startForNewTenant(10L, SubscriptionTier.FREE);
        when(subscriptionRepository.findByTenantId(10L)).thenAnswer(inv ->
                Optional.of(TenantSubscription.builder().id(100L).tenantId(10L).plan(basic)
                        .status(SubscriptionStatus.ACTIVE).startedAt(LocalDateTime.now()).build()));
        when(razorpayConfigResolver.resolve()).thenReturn(
                new EffectiveRazorpayConfig(true, "key", "secret", true, "whsec", "https://api.razorpay.com/v1", 59900, 99900));
        authenticateAsTenant(10L);

        assertThatThrownBy(() -> service.changePlan("PREMIUM", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("UPGRADE_REQUIRES_CHECKOUT"));
    }

    @Test
    @DisplayName("a downgrade is always self-service even when a payment gateway is active")
    void downgradeAllowedWhenGatewayActive() {
        when(subscriptionRepository.findByTenantId(10L)).thenAnswer(inv ->
                Optional.of(TenantSubscription.builder().id(100L).tenantId(10L).plan(premium)
                        .status(SubscriptionStatus.ACTIVE).startedAt(LocalDateTime.now()).build()));
        when(razorpayConfigResolver.resolve()).thenReturn(
                new EffectiveRazorpayConfig(true, "key", "secret", true, "whsec", "https://api.razorpay.com/v1", 59900, 99900));
        authenticateAsTenant(10L);

        var response = service.changePlan("BASIC", "no longer needed");

        assertThat(response.planCode()).isEqualTo("BASIC");
        assertThat(tenant.getSubscriptionTier()).isEqualTo(SubscriptionTier.FREE);
    }

    @Test
    @DisplayName("cancel() on the Basic plan is refused - there is nothing to cancel")
    void cancelRefusedOnBasic() {
        when(subscriptionRepository.findByTenantId(10L)).thenAnswer(inv ->
                Optional.of(TenantSubscription.builder().id(100L).tenantId(10L).plan(basic)
                        .status(SubscriptionStatus.ACTIVE).startedAt(LocalDateTime.now()).build()));
        authenticateAsTenant(10L);

        assertThatThrownBy(() -> service.cancel("changed my mind"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("cancel() on a paid plan sets status CANCELLED and records the reason")
    void cancelOnPaidPlan() {
        when(subscriptionRepository.findByTenantId(10L)).thenAnswer(inv ->
                Optional.of(TenantSubscription.builder().id(100L).tenantId(10L).plan(pro)
                        .status(SubscriptionStatus.ACTIVE).startedAt(LocalDateTime.now()).build()));
        authenticateAsTenant(10L);

        var response = service.cancel("too expensive");

        assertThat(response.status()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("currentFor() lazily reverts an expired TRIAL to Basic ACTIVE")
    void currentForRevertsExpiredTrial() {
        TenantSubscription expiredTrial = TenantSubscription.builder().id(100L).tenantId(10L).plan(premium)
                .status(SubscriptionStatus.TRIAL).trialEndsAt(LocalDateTime.now().minusDays(1))
                .startedAt(LocalDateTime.now().minusDays(15)).build();
        when(subscriptionRepository.findByTenantId(10L)).thenReturn(Optional.of(expiredTrial));

        TenantSubscription result = service.currentFor(10L);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getPlan().getTier()).isEqualTo(SubscriptionTier.FREE);
        assertThat(tenant.getSubscriptionTier()).isEqualTo(SubscriptionTier.FREE);
    }

    @Test
    @DisplayName("every transition writes exactly one subscription_history row")
    void transitionWritesHistory() {
        service.startForNewTenant(10L, SubscriptionTier.PRO);

        ArgumentCaptor<SubscriptionHistory> captor = ArgumentCaptor.forClass(SubscriptionHistory.class);
        org.mockito.Mockito.verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getToPlanCode()).isEqualTo("PRO");
        assertThat(captor.getValue().getToStatus()).isEqualTo("ACTIVE");
    }
}
