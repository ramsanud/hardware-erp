package com.hardware.erp.subscription.service.impl;

import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.subscription.dto.FeatureAccessResponse;
import com.hardware.erp.subscription.entity.Feature;
import com.hardware.erp.subscription.entity.FeatureKey;
import com.hardware.erp.subscription.entity.SubscriptionPlan;
import com.hardware.erp.subscription.entity.TenantSubscription;
import com.hardware.erp.subscription.exception.FeatureNotAvailableException;
import com.hardware.erp.subscription.repository.FeatureRepository;
import com.hardware.erp.subscription.repository.PlanFeatureRepository;
import com.hardware.erp.subscription.repository.SubscriptionPlanRepository;
import com.hardware.erp.subscription.service.FeatureAccessService;
import com.hardware.erp.subscription.service.SubscriptionLifecycleService;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * CR-088. The plan-feature matrix is a few dozen rows that change only
 * when a platform operator edits them, so it is read once and cached in
 * memory; refresh() drops the cache. The per-tenant part (which plan, and
 * whether the subscription still grants it) is a single-row lookup that
 * SubscriptionLifecycleService.currentFor() owns, including the lazy
 * trial/expiry transitions.
 */
@Service
@RequiredArgsConstructor
public class FeatureAccessServiceImpl implements FeatureAccessService {

    private final PlanFeatureRepository planFeatureRepository;
    private final SubscriptionPlanRepository planRepository;
    private final FeatureRepository featureRepository;
    private final SubscriptionLifecycleService lifecycleService;

    private volatile Map<String, Set<FeatureKey>> matrix;

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlan effectivePlan(Long tenantId) {
        TenantSubscription subscription = lifecycleService.currentFor(tenantId);
        if (subscription.getStatus().grantsPaidFeatures()) {
            return subscription.getPlan();
        }
        // Expired / cancelled / suspended: the shop keeps its data and the
        // daily-operations features, nothing more (spec §13).
        return planRepository.findByTier(SubscriptionTier.FREE)
                .orElseThrow(() -> new IllegalStateException("BASIC plan row missing - V57 not applied"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasFeature(Long tenantId, FeatureKey feature) {
        return featuresOf(effectivePlan(tenantId).getPlanCode()).contains(feature);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireFeature(FeatureKey feature) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        SubscriptionPlan current = effectivePlan(tenantId);
        if (featuresOf(current.getPlanCode()).contains(feature)) {
            return;
        }
        SubscriptionPlan required = cheapestPlanWith(feature);
        throw new FeatureNotAvailableException(
                feature.name(), featureName(feature),
                current.getPlanCode(), current.getPlanName(),
                required == null ? null : required.getPlanCode(),
                required == null ? "a higher" : required.getPlanName());
    }

    @Override
    public Set<FeatureKey> featuresOf(String planCode) {
        return matrix().getOrDefault(planCode, Collections.emptySet());
    }

    @Override
    @Transactional(readOnly = true)
    public FeatureAccessResponse access(Long tenantId, FeatureKey feature) {
        SubscriptionPlan current = effectivePlan(tenantId);
        boolean allowed = featuresOf(current.getPlanCode()).contains(feature);
        SubscriptionPlan required = allowed ? null : cheapestPlanWith(feature);
        return new FeatureAccessResponse(feature.name(), featureName(feature), allowed,
                current.getPlanCode(), current.getPlanName(),
                required == null ? null : required.getPlanCode(),
                required == null ? null : required.getPlanName());
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlan cheapestPlanWith(FeatureKey feature) {
        return planRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .filter(plan -> featuresOf(plan.getPlanCode()).contains(feature))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void refresh() {
        matrix = null;
    }

    private String featureName(FeatureKey feature) {
        return featureRepository.findByFeatureKey(feature.name())
                .map(Feature::getFeatureName)
                .orElse(feature.name());
    }

    private Map<String, Set<FeatureKey>> matrix() {
        Map<String, Set<FeatureKey>> local = matrix;
        if (local == null) {
            synchronized (this) {
                local = matrix;
                if (local == null) {
                    local = load();
                    matrix = local;
                }
            }
        }
        return local;
    }

    private Map<String, Set<FeatureKey>> load() {
        Map<String, Set<FeatureKey>> loaded = new HashMap<>();
        for (Object[] row : planFeatureRepository.matrix()) {
            String planCode = (String) row[0];
            String key = (String) row[1];
            FeatureKey featureKey;
            try {
                featureKey = FeatureKey.valueOf(key);
            } catch (IllegalArgumentException unknownToThisBuild) {
                // A row added by a newer migration than this binary knows -
                // harmless, it simply cannot be required from code yet.
                continue;
            }
            loaded.computeIfAbsent(planCode, ignored -> EnumSet.noneOf(FeatureKey.class)).add(featureKey);
        }
        return Collections.unmodifiableMap(loaded);
    }
}
