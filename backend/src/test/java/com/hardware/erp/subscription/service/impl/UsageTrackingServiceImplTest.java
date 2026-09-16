package com.hardware.erp.subscription.service.impl;

import com.hardware.erp.subscription.entity.PlanUsageLimit;
import com.hardware.erp.subscription.entity.SubscriptionPlan;
import com.hardware.erp.subscription.entity.SubscriptionUsage;
import com.hardware.erp.subscription.entity.UsageKey;
import com.hardware.erp.subscription.exception.UsageLimitReachedException;
import com.hardware.erp.subscription.repository.PlanUsageLimitRepository;
import com.hardware.erp.subscription.repository.SubscriptionUsageRepository;
import com.hardware.erp.subscription.service.FeatureAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsageTrackingServiceImplTest {

    @Mock private SubscriptionUsageRepository usageRepository;
    @Mock private PlanUsageLimitRepository planUsageLimitRepository;
    @Mock private FeatureAccessService featureAccessService;

    @InjectMocks private UsageTrackingServiceImpl service;

    private SubscriptionPlan pro;

    @BeforeEach
    void setUp() {
        pro = new SubscriptionPlan();
        pro.setId(2L);
        pro.setPlanCode("PRO");
        when(featureAccessService.effectivePlan(10L)).thenReturn(pro);

        PlanUsageLimit whatsappLimit = new PlanUsageLimit();
        whatsappLimit.setUsageKey(UsageKey.WHATSAPP);
        whatsappLimit.setIncludedCount(500L);
        when(planUsageLimitRepository.findByPlanIdAndUsageKey(2L, UsageKey.WHATSAPP))
                .thenReturn(Optional.of(whatsappLimit));
    }

    @Test
    @DisplayName("a unit is granted when the shop's monthly consume() call succeeds")
    void grantsUnitWhenUnderLimit() {
        when(usageRepository.consume(eq(10L), eq("WHATSAPP"), any(LocalDate.class), eq(1L), eq(500L)))
                .thenReturn(1);

        assertThat(service.tryConsume(10L, UsageKey.WHATSAPP)).isTrue();
    }

    @Test
    @DisplayName("no unit is granted once the shop's plan-included count for the month is used up")
    void refusesWhenAtLimit() {
        when(usageRepository.consume(eq(10L), eq("WHATSAPP"), any(LocalDate.class), eq(1L), eq(500L)))
                .thenReturn(0);

        assertThat(service.tryConsume(10L, UsageKey.WHATSAPP)).isFalse();
    }

    @Test
    @DisplayName("a channel with zero included units on the plan is refused without ever touching the database counter")
    void zeroIncludedCountRefusesWithoutQuery() {
        when(planUsageLimitRepository.findByPlanIdAndUsageKey(2L, UsageKey.SMS)).thenReturn(Optional.empty());

        assertThat(service.tryConsume(10L, UsageKey.SMS)).isFalse();
        org.mockito.Mockito.verifyNoInteractions(usageRepository);
    }

    @Test
    @DisplayName("consumeOrThrow throws UsageLimitReachedException naming used/included counts when refused")
    void consumeOrThrowThrowsWithCounts() {
        when(usageRepository.consume(eq(10L), eq("WHATSAPP"), any(LocalDate.class), eq(1L), eq(500L)))
                .thenReturn(0);
        SubscriptionUsage existing = SubscriptionUsage.builder().usedCount(500L).build();
        when(usageRepository.findByTenantIdAndUsageKeyAndPeriodStart(eq(10L), eq(UsageKey.WHATSAPP), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.consumeOrThrow(10L, UsageKey.WHATSAPP))
                .isInstanceOf(UsageLimitReachedException.class)
                .satisfies(ex -> {
                    UsageLimitReachedException ule = (UsageLimitReachedException) ex;
                    assertThat(ule.getUsedCount()).isEqualTo(500L);
                    assertThat(ule.getIncludedCount()).isEqualTo(500L);
                });
    }

    @Test
    @DisplayName("usage() reports used/included/remaining for every metered channel the plan carries")
    void usageReportsPerChannel() {
        when(planUsageLimitRepository.findByPlanId(2L)).thenReturn(List.of(
                limit(UsageKey.WHATSAPP, 500L), limit(UsageKey.EMAIL, 1000L)));
        LocalDate periodStart = YearMonth.now().atDay(1);
        when(usageRepository.findByTenantIdAndUsageKeyAndPeriodStart(10L, UsageKey.WHATSAPP, periodStart))
                .thenReturn(Optional.of(SubscriptionUsage.builder().usedCount(340L).build()));
        when(usageRepository.findByTenantIdAndUsageKeyAndPeriodStart(10L, UsageKey.EMAIL, periodStart))
                .thenReturn(Optional.empty());

        var usage = service.usage(10L);

        assertThat(usage.planCode()).isEqualTo("PRO");
        assertThat(usage.items()).hasSize(2);
        var whatsapp = usage.items().stream().filter(i -> i.usageKey().equals("WHATSAPP")).findFirst().orElseThrow();
        assertThat(whatsapp.usedCount()).isEqualTo(340L);
        assertThat(whatsapp.remainingCount()).isEqualTo(160L);
        assertThat(whatsapp.limitReached()).isFalse();
        var email = usage.items().stream().filter(i -> i.usageKey().equals("EMAIL")).findFirst().orElseThrow();
        assertThat(email.usedCount()).isEqualTo(0L);
    }

    private PlanUsageLimit limit(UsageKey key, long included) {
        PlanUsageLimit l = new PlanUsageLimit();
        l.setUsageKey(key);
        l.setIncludedCount(included);
        return l;
    }
}
