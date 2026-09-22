package com.hardware.erp.subscription.service.impl;

import com.hardware.erp.subscription.dto.UsageItemResponse;
import com.hardware.erp.subscription.dto.UsageResponse;
import com.hardware.erp.subscription.entity.PlanUsageLimit;
import com.hardware.erp.subscription.entity.SubscriptionPlan;
import com.hardware.erp.subscription.entity.SubscriptionUsage;
import com.hardware.erp.subscription.entity.UsageKey;
import com.hardware.erp.subscription.exception.UsageLimitReachedException;
import com.hardware.erp.subscription.repository.PlanUsageLimitRepository;
import com.hardware.erp.subscription.repository.SubscriptionUsageRepository;
import com.hardware.erp.subscription.service.FeatureAccessService;
import com.hardware.erp.subscription.service.UsageTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * CR-088 §15. tryConsume() runs its own REQUIRES_NEW transaction so a
 * caller mid-way through a longer @Transactional method (sending an
 * invoice email, say) still gets an immediate, committed answer before it
 * decides whether to call the paid provider at all - and so the counter
 * is not rolled back if something later in that caller's transaction
 * fails for an unrelated reason.
 */
@Service
@RequiredArgsConstructor
public class UsageTrackingServiceImpl implements UsageTrackingService {

    private final SubscriptionUsageRepository usageRepository;
    private final PlanUsageLimitRepository planUsageLimitRepository;
    private final FeatureAccessService featureAccessService;

    /**
     * Also REQUIRES_NEW, not just a plain delegate - BUG-BE-002's own
     * trap. NotificationServiceImpl's async send path calls this two-arg
     * overload through the Spring proxy (an external call, so its
     * annotation is honoured), but the three-arg overload it delegates to
     * is called as {@code this.tryConsume(...)} - a same-class
     * self-invocation that bypasses the proxy and runs with no
     * transaction at all, throwing TransactionRequiredException on every
     * call (found by CR-091's OfflineSyncIT work turning up the resulting
     * "Async method notifyInvoiceCreated failed" log on every invoice
     * creation). Annotating this overload too means the proxy opens the
     * transaction here, before the self-invoked call ever runs - the
     * inner call executes inside it either way.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryConsume(Long tenantId, UsageKey usageKey) {
        return tryConsume(tenantId, usageKey, 1L);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryConsume(Long tenantId, UsageKey usageKey, long units) {
        SubscriptionPlan plan = featureAccessService.effectivePlan(tenantId);
        long includedCount = planUsageLimitRepository.findByPlanIdAndUsageKey(plan.getId(), usageKey)
                .map(PlanUsageLimit::getIncludedCount)
                .orElse(0L);
        if (includedCount <= 0) {
            return false;
        }
        LocalDate periodStart = YearMonth.now().atDay(1);
        int updated = usageRepository.consume(tenantId, usageKey.name(), periodStart, units, includedCount);
        return updated > 0;
    }

    @Override
    public void consumeOrThrow(Long tenantId, UsageKey usageKey) {
        if (tryConsume(tenantId, usageKey)) {
            return;
        }
        SubscriptionPlan plan = featureAccessService.effectivePlan(tenantId);
        long includedCount = planUsageLimitRepository.findByPlanIdAndUsageKey(plan.getId(), usageKey)
                .map(PlanUsageLimit::getIncludedCount).orElse(0L);
        long usedCount = currentUsage(tenantId, usageKey);
        throw new UsageLimitReachedException(usageKey.name(), label(usageKey), usedCount, includedCount);
    }

    @Override
    @Transactional(readOnly = true)
    public UsageResponse usage(Long tenantId) {
        SubscriptionPlan plan = featureAccessService.effectivePlan(tenantId);
        LocalDate periodStart = YearMonth.now().atDay(1);
        LocalDate periodEnd = YearMonth.now().atEndOfMonth();
        List<PlanUsageLimit> limits = planUsageLimitRepository.findByPlanId(plan.getId());

        List<UsageItemResponse> items = limits.stream()
                .sorted(Comparator.comparingInt(limit -> limit.getUsageKey().ordinal()))
                .map(limit -> {
                    long used = usageRepository.findByTenantIdAndUsageKeyAndPeriodStart(
                                    tenantId, limit.getUsageKey(), periodStart)
                            .map(SubscriptionUsage::getUsedCount).orElse(0L);
                    long remaining = Math.max(0, limit.getIncludedCount() - used);
                    return new UsageItemResponse(limit.getUsageKey().name(), label(limit.getUsageKey()),
                            used, limit.getIncludedCount(), remaining, used >= limit.getIncludedCount());
                })
                .toList();

        return new UsageResponse(periodStart, periodEnd, plan.getPlanCode(), items);
    }

    private long currentUsage(Long tenantId, UsageKey usageKey) {
        LocalDate periodStart = YearMonth.now().atDay(1);
        return usageRepository.findByTenantIdAndUsageKeyAndPeriodStart(tenantId, usageKey, periodStart)
                .map(SubscriptionUsage::getUsedCount).orElse(0L);
    }

    static String label(UsageKey key) {
        return switch (key) {
            case WHATSAPP -> "WhatsApp messages";
            case SMS -> "SMS messages";
            case EMAIL -> "Emails";
            case AI_REQUEST -> "AI requests";
            case STORAGE_MB -> "Storage (MB)";
        };
    }
}
