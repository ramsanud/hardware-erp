package com.hardware.erp.subscription.repository;

import com.hardware.erp.subscription.entity.SubscriptionPlan;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByPlanCode(String planCode);

    Optional<SubscriptionPlan> findByTier(SubscriptionTier tier);

    List<SubscriptionPlan> findByActiveTrueOrderByDisplayOrderAsc();
}
