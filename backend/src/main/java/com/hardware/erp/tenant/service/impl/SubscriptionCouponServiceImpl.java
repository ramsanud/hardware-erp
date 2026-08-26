package com.hardware.erp.tenant.service.impl;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.dto.SubscriptionCouponRedemptionResponse;
import com.hardware.erp.tenant.dto.SubscriptionCouponRequest;
import com.hardware.erp.tenant.dto.SubscriptionCouponResponse;
import com.hardware.erp.tenant.entity.SubscriptionCoupon;
import com.hardware.erp.tenant.entity.SubscriptionCouponStatus;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.SubscriptionCouponRepository;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.service.SubscriptionCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SubscriptionCouponServiceImpl implements SubscriptionCouponService {

    private static final String MODULE = "SUBSCRIPTION_COUPON";
    private static final String ENTITY = "SUBSCRIPTION_COUPON";

    private final SubscriptionCouponRepository couponRepository;
    private final TenantRepository tenantRepository;
    private final ActivityLogService activityLog;

    @Override
    @Transactional
    public SubscriptionCouponResponse create(SubscriptionCouponRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        if (couponRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, request.code().trim())) {
            throw new DuplicateResourceException("Coupon code", request.code());
        }
        validateBusinessRules(request);

        SubscriptionCoupon coupon = SubscriptionCoupon.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .code(request.code().trim().toUpperCase())
                .description(blankToNull(request.description()))
                .grantedTier(request.grantedTier())
                .trialDays(request.trialDays())
                .validFrom(request.validFrom())
                .validUntil(request.validUntil())
                .usageLimit(request.usageLimit())
                .status(request.status())
                .build();

        SubscriptionCoupon saved = couponRepository.save(coupon);
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getCode(),
                Map.of("grantedTier", saved.getGrantedTier(), "trialDays", saved.getTrialDays()));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SubscriptionCouponResponse update(Long id, SubscriptionCouponRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        SubscriptionCoupon coupon = require(id, tenantId);
        if (couponRepository.existsByTenantIdAndCodeIgnoreCaseAndIdNot(tenantId, request.code().trim(), id)) {
            throw new DuplicateResourceException("Coupon code", request.code());
        }
        validateBusinessRules(request);

        coupon.setCode(request.code().trim().toUpperCase());
        coupon.setDescription(blankToNull(request.description()));
        coupon.setGrantedTier(request.grantedTier());
        coupon.setTrialDays(request.trialDays());
        coupon.setValidFrom(request.validFrom());
        coupon.setValidUntil(request.validUntil());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setStatus(request.status());

        SubscriptionCoupon saved = couponRepository.save(coupon);
        activityLog.updated(MODULE, ENTITY, saved.getId(), saved.getCode(), Map.of(), Map.of("status", saved.getStatus()));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SubscriptionCouponResponse> search(String search, SubscriptionCouponStatus status, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(couponRepository.search(tenantId, search, status, pageable), this::toResponse);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        SubscriptionCoupon coupon = require(id, tenantId);
        couponRepository.delete(coupon);
        activityLog.deleted(MODULE, ENTITY, coupon.getId(), coupon.getCode(), "Subscription coupon deleted");
    }

    /**
     * Always fully replaces any trial already in progress (no stacking) -
     * the simplest, least surprising behaviour, matching how the manual
     * tier picker in Shop Settings has always worked (whatever you pick
     * last wins).
     */
    @Override
    @Transactional
    public SubscriptionCouponRedemptionResponse redeem(String code) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        SubscriptionCoupon coupon = couponRepository.findByTenantIdAndCodeIgnoreCase(tenantId, code.trim())
                .orElseThrow(() -> new BusinessException("This coupon code doesn't exist."));

        if (coupon.getStatus() != SubscriptionCouponStatus.ACTIVE) {
            throw new BusinessException("This coupon is no longer active.");
        }
        LocalDate today = LocalDate.now();
        if (coupon.getValidFrom() != null && today.isBefore(coupon.getValidFrom())) {
            throw new BusinessException("This coupon isn't valid yet.");
        }
        if (coupon.getValidUntil() != null && today.isAfter(coupon.getValidUntil())) {
            throw new BusinessException("This coupon has expired.");
        }
        if (coupon.getUsageLimit() != null && coupon.getTimesUsed() >= coupon.getUsageLimit()) {
            throw new BusinessException("This coupon has already been used the maximum number of times.");
        }

        Tenant tenant = tenantRepository.getReferenceById(tenantId);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(coupon.getTrialDays());
        tenant.setSubscriptionTier(coupon.getGrantedTier());
        tenant.setSubscriptionTrialExpiresAt(expiresAt);
        tenantRepository.save(tenant);

        coupon.setTimesUsed(coupon.getTimesUsed() + 1);
        couponRepository.save(coupon);

        activityLog.updated(MODULE, "TENANT", tenantId, coupon.getCode(),
                Map.of(), Map.of("grantedTier", coupon.getGrantedTier(), "trialExpiresAt", expiresAt));

        return new SubscriptionCouponRedemptionResponse(coupon.getGrantedTier(), expiresAt);
    }

    // ---------------------------------------------------------------

    private void validateBusinessRules(SubscriptionCouponRequest request) {
        if (request.validFrom() != null && request.validUntil() != null
                && request.validFrom().isAfter(request.validUntil())) {
            throw new BusinessException("Valid-from date must be on or before the valid-until date.");
        }
    }

    private SubscriptionCoupon require(Long id, Long tenantId) {
        return couponRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription coupon", id));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private SubscriptionCouponResponse toResponse(SubscriptionCoupon coupon) {
        return new SubscriptionCouponResponse(
                coupon.getId(), coupon.getCode(), coupon.getDescription(),
                coupon.getGrantedTier(), coupon.getTrialDays(),
                coupon.getValidFrom(), coupon.getValidUntil(),
                coupon.getUsageLimit(), coupon.getTimesUsed(),
                coupon.getStatus(), coupon.isCurrentlyRedeemable());
    }
}
