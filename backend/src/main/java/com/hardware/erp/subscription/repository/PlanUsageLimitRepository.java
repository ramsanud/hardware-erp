package com.hardware.erp.subscription.repository;

import com.hardware.erp.subscription.entity.PlanUsageLimit;
import com.hardware.erp.subscription.entity.UsageKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanUsageLimitRepository extends JpaRepository<PlanUsageLimit, Long> {

    Optional<PlanUsageLimit> findByPlanIdAndUsageKey(Long planId, UsageKey usageKey);

    List<PlanUsageLimit> findByPlanId(Long planId);
}
