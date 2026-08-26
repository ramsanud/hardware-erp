package com.hardware.erp.tenant.service.impl;

import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final TenantRepository tenantRepository;

    /**
     * REQUIRES_NEW, not just non-readOnly: this is called from inside
     * readOnly=true callers too (EntitlementServiceImpl's requireCanAdd*()
     * methods, TenantSettingsServiceImpl.get()) - a caller's readOnly flag
     * puts Hibernate into a flush-suppressing mode for the whole physical
     * transaction, so joining that transaction (the PROPAGATION_REQUIRED
     * default) would silently make the FREE-revert write below never
     * actually flush. A fresh REQUIRES_NEW transaction sidesteps that
     * entirely - the trial-expiry revert (CR-032) always commits regardless
     * of what transaction called this. Called very often (every
     * entitlement/feature-gate check) but it is a single-row lookup either
     * way; the occasional extra UPDATE on expiry is not worth a scheduled
     * job for.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SubscriptionTier currentTier() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return SubscriptionTier.FREE;
        }
        if (tenant.getSubscriptionTrialExpiresAt() != null
                && LocalDateTime.now().isAfter(tenant.getSubscriptionTrialExpiresAt())) {
            tenant.setSubscriptionTier(SubscriptionTier.FREE);
            tenant.setSubscriptionTrialExpiresAt(null);
            tenantRepository.save(tenant);
        }
        return tenant.getSubscriptionTier();
    }

    @Override
    public void requireTier(SubscriptionTier minimum) {
        SubscriptionTier current = currentTier();
        if (current.ordinal() < minimum.ordinal()) {
            throw new BusinessException(
                    "This feature needs the " + minimum.displayName() + " plan. Your shop is currently on "
                            + current.displayName() + ". Upgrade the plan in Shop Settings to use it.",
                    HttpStatus.PAYMENT_REQUIRED, "SUBSCRIPTION_TIER_REQUIRED");
        }
    }
}
