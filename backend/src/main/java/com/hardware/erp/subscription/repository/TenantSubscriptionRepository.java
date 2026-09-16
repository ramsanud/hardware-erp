package com.hardware.erp.subscription.repository;

import com.hardware.erp.subscription.entity.SubscriptionStatus;
import com.hardware.erp.subscription.entity.TenantSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, Long> {

    /**
     * JOIN FETCH the plan: SubscriptionLifecycleServiceImpl.currentFor()
     * loads this inside its own REQUIRES_NEW transaction and hands the
     * entity back to a caller (FeatureAccessServiceImpl.effectivePlan(),
     * itself often a *different* transaction/session) that then reads
     * getPlan().getPlanCode() - a lazy proxy would throw
     * LazyInitializationException the moment the owning session has
     * closed, which happens as soon as currentFor()'s transaction commits.
     * `plan` is a 3-row lookup table; fetching it eagerly here costs one
     * join, not an extra round trip.
     */
    @Query("SELECT ts FROM TenantSubscription ts JOIN FETCH ts.plan WHERE ts.tenantId = :tenantId")
    Optional<TenantSubscription> findByTenantId(Long tenantId);

    List<TenantSubscription> findByStatus(SubscriptionStatus status);
}
