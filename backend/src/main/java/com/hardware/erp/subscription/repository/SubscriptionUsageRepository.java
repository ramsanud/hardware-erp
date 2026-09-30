package com.hardware.erp.subscription.repository;

import com.hardware.erp.subscription.entity.SubscriptionUsage;
import com.hardware.erp.subscription.entity.UsageKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionUsageRepository extends JpaRepository<SubscriptionUsage, Long> {

    Optional<SubscriptionUsage> findByTenantIdAndUsageKeyAndPeriodStart(Long tenantId, UsageKey usageKey, LocalDate periodStart);

    List<SubscriptionUsage> findByTenantIdAndPeriodStart(Long tenantId, LocalDate periodStart);

    /**
     * Atomic upsert-and-increment, guarded by the included count so two
     * concurrent sends cannot both squeeze under the limit. Returns 1 when
     * the unit was granted, 0 when the shop is already at its limit.
     */
    @Modifying
    @Query(value = """
            INSERT INTO subscription_usage (tenant_id, usage_key, period_start, used_count, created_at)
            VALUES (:tenantId, :usageKey, :periodStart, :units, clock_timestamp())
            ON CONFLICT (tenant_id, usage_key, period_start) DO UPDATE
               SET used_count = subscription_usage.used_count + :units,
                   updated_at = clock_timestamp()
             WHERE subscription_usage.used_count + :units <= :includedCount
            """, nativeQuery = true)
    int consume(Long tenantId, String usageKey, LocalDate periodStart, long units, long includedCount);
}
