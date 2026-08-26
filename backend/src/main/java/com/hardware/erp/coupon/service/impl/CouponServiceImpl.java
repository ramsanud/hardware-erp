package com.hardware.erp.coupon.service.impl;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.coupon.dto.CouponDiscountResult;
import com.hardware.erp.coupon.dto.CouponRequest;
import com.hardware.erp.coupon.dto.CouponResponse;
import com.hardware.erp.coupon.dto.CouponSummaryResponse;
import com.hardware.erp.coupon.entity.Coupon;
import com.hardware.erp.coupon.entity.CouponStatus;
import com.hardware.erp.coupon.entity.DiscountType;
import com.hardware.erp.coupon.repository.CouponRepository;
import com.hardware.erp.coupon.service.CouponService;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private static final String MODULE = "COUPON";
    private static final String ENTITY = "COUPON";

    private final CouponRepository couponRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final ActivityLogService activityLog;

    @Override
    @Transactional
    public CouponResponse create(CouponRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        if (couponRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, request.code().trim())) {
            throw new DuplicateResourceException("Coupon code", request.code());
        }
        validateBusinessRules(request);

        Coupon coupon = Coupon.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .code(request.code().trim().toUpperCase())
                .description(blankToNull(request.description()))
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .minPurchasePaise(request.minPurchasePaise())
                .maxDiscountPaise(request.maxDiscountPaise())
                .validFrom(request.validFrom())
                .validUntil(request.validUntil())
                .usageLimit(request.usageLimit())
                .status(request.status())
                .products(resolveProducts(request.productIds(), tenantId))
                .build();

        Coupon saved = couponRepository.save(coupon);
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getCode(), Map.of("discountType", saved.getDiscountType()));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CouponResponse update(Long id, CouponRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Coupon coupon = require(id, tenantId);
        if (couponRepository.existsByTenantIdAndCodeIgnoreCaseAndIdNot(tenantId, request.code().trim(), id)) {
            throw new DuplicateResourceException("Coupon code", request.code());
        }
        validateBusinessRules(request);

        coupon.setCode(request.code().trim().toUpperCase());
        coupon.setDescription(blankToNull(request.description()));
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(request.discountValue());
        coupon.setMinPurchasePaise(request.minPurchasePaise());
        coupon.setMaxDiscountPaise(request.maxDiscountPaise());
        coupon.setValidFrom(request.validFrom());
        coupon.setValidUntil(request.validUntil());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setStatus(request.status());
        coupon.setProducts(resolveProducts(request.productIds(), tenantId));

        Coupon saved = couponRepository.save(coupon);
        activityLog.updated(MODULE, ENTITY, saved.getId(), saved.getCode(), Map.of(), Map.of("status", saved.getStatus()));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CouponResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return toResponse(require(id, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponSummaryResponse> search(String search, CouponStatus status, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(couponRepository.search(tenantId, search, status, pageable), this::toSummary);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Coupon coupon = require(id, tenantId);
        couponRepository.delete(coupon);
        activityLog.deleted(MODULE, ENTITY, coupon.getId(), coupon.getCode(), "Coupon deleted");
    }

    @Override
    @Transactional(readOnly = true)
    public CouponDiscountResult calculateDiscount(String code, Map<Long, Long> lineSubtotalPaiseByProductId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Coupon coupon = couponRepository.findByTenantIdAndCodeIgnoreCase(tenantId, code.trim())
                .orElseThrow(() -> new BusinessException("This coupon code doesn't exist."));

        if (coupon.getStatus() != CouponStatus.ACTIVE) {
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

        Map<Long, Long> eligibleLines = new LinkedHashMap<>();
        if (coupon.isRestrictedToProducts()) {
            Set<Long> allowedProductIds = coupon.getProducts().stream().map(Product::getId)
                    .collect(java.util.stream.Collectors.toSet());
            lineSubtotalPaiseByProductId.forEach((productId, subtotal) -> {
                if (allowedProductIds.contains(productId)) {
                    eligibleLines.put(productId, subtotal);
                }
            });
            if (eligibleLines.isEmpty()) {
                throw new BusinessException("This coupon doesn't apply to any product in this order.");
            }
        } else {
            eligibleLines.putAll(lineSubtotalPaiseByProductId);
        }

        long eligibleSubtotal = eligibleLines.values().stream().mapToLong(Long::longValue).sum();
        if (coupon.getMinPurchasePaise() != null && eligibleSubtotal < coupon.getMinPurchasePaise()) {
            throw new BusinessException("This coupon needs a minimum purchase of "
                    + IndianCurrencyFormat.rupees(coupon.getMinPurchasePaise()) + " on eligible items.");
        }

        long totalDiscount = computeDiscount(coupon, eligibleSubtotal);
        Map<Long, Long> allocation = allocateProportionally(eligibleLines, eligibleSubtotal, totalDiscount);

        return new CouponDiscountResult(coupon.getId(), coupon.getCode(), totalDiscount, allocation);
    }

    @Override
    @Transactional
    public void recordUsage(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", couponId));
        coupon.setTimesUsed(coupon.getTimesUsed() + 1);
        couponRepository.save(coupon);
    }

    // ---------------------------------------------------------------

    private long computeDiscount(Coupon coupon, long eligibleSubtotal) {
        long raw;
        if (coupon.getDiscountType() == DiscountType.PERCENT) {
            raw = BigDecimal.valueOf(eligibleSubtotal)
                    .multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValueExact();
            if (coupon.getMaxDiscountPaise() != null) {
                raw = Math.min(raw, coupon.getMaxDiscountPaise());
            }
        } else {
            raw = coupon.getDiscountValue().longValueExact();
        }
        return Math.min(raw, eligibleSubtotal);
    }

    /** Gives the last line whatever's left over, so the parts always sum to exactly totalDiscount despite rounding. */
    private Map<Long, Long> allocateProportionally(Map<Long, Long> eligibleLines, long eligibleSubtotal, long totalDiscount) {
        Map<Long, Long> allocation = new LinkedHashMap<>();
        if (totalDiscount <= 0 || eligibleSubtotal <= 0) {
            eligibleLines.keySet().forEach(id -> allocation.put(id, 0L));
            return allocation;
        }
        long remaining = totalDiscount;
        List<Long> productIds = List.copyOf(eligibleLines.keySet());
        for (int i = 0; i < productIds.size(); i++) {
            Long productId = productIds.get(i);
            long share;
            if (i == productIds.size() - 1) {
                share = remaining;
            } else {
                share = BigDecimal.valueOf(totalDiscount)
                        .multiply(BigDecimal.valueOf(eligibleLines.get(productId)))
                        .divide(BigDecimal.valueOf(eligibleSubtotal), 0, RoundingMode.DOWN)
                        .longValueExact();
                remaining -= share;
            }
            allocation.put(productId, share);
        }
        return allocation;
    }

    private Set<Product> resolveProducts(List<Long> productIds, Long tenantId) {
        if (productIds == null || productIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Product> found = productRepository.findAllByIdInAndTenantId(productIds, tenantId);
        if (found.size() != Set.copyOf(productIds).size()) {
            throw new BusinessException("One or more selected products could not be found.");
        }
        return new LinkedHashSet<>(found);
    }

    private void validateBusinessRules(CouponRequest request) {
        if (request.discountType() == DiscountType.PERCENT
                && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("A percentage discount cannot exceed 100.");
        }
        if (request.validFrom() != null && request.validUntil() != null
                && request.validFrom().isAfter(request.validUntil())) {
            throw new BusinessException("Valid-from date must be on or before the valid-until date.");
        }
    }

    private Coupon require(Long id, Long tenantId) {
        return couponRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", id));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private CouponSummaryResponse toSummary(Coupon coupon) {
        return new CouponSummaryResponse(
                coupon.getId(), coupon.getCode(), coupon.getDiscountType(), coupon.getDiscountValue(),
                coupon.getUsageLimit(), coupon.getTimesUsed(), coupon.getStatus(),
                coupon.isCurrentlyValid(), coupon.isRestrictedToProducts());
    }

    private CouponResponse toResponse(Coupon coupon) {
        List<CouponResponse.ProductRef> products = coupon.getProducts().stream()
                .map(p -> new CouponResponse.ProductRef(p.getId(), p.getProductCode(), p.getProductName()))
                .toList();
        return new CouponResponse(
                coupon.getId(), coupon.getCode(), coupon.getDescription(),
                coupon.getDiscountType(), coupon.getDiscountValue(),
                coupon.getMinPurchasePaise(),
                coupon.getMinPurchasePaise() != null ? IndianCurrencyFormat.rupees(coupon.getMinPurchasePaise()) : null,
                coupon.getMaxDiscountPaise(),
                coupon.getMaxDiscountPaise() != null ? IndianCurrencyFormat.rupees(coupon.getMaxDiscountPaise()) : null,
                coupon.getValidFrom(), coupon.getValidUntil(), coupon.getUsageLimit(), coupon.getTimesUsed(),
                coupon.getStatus(), coupon.isCurrentlyValid(), products);
    }
}
