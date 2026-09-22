package com.hardware.erp.discovery.service.impl;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.util.PhoneNumberNormalizer;
import com.hardware.erp.discovery.dto.DiscoveryDtos.DiscoverySettingRequest;
import com.hardware.erp.discovery.dto.DiscoveryDtos.DiscoverySettingResponse;
import com.hardware.erp.discovery.dto.DiscoveryDtos.NearbyAvailabilityResponse;
import com.hardware.erp.discovery.dto.DiscoveryDtos.NearbyShopResponse;
import com.hardware.erp.discovery.entity.OwnerNotificationType;
import com.hardware.erp.discovery.entity.ProductRequestDiscoveryMatch;
import com.hardware.erp.discovery.entity.ShopDiscoverySetting;
import com.hardware.erp.discovery.repository.ProductRequestDiscoveryMatchRepository;
import com.hardware.erp.discovery.repository.ShopDiscoveryRepository;
import com.hardware.erp.discovery.repository.ShopDiscoveryRepository.NearbyMatch;
import com.hardware.erp.discovery.repository.ShopDiscoverySettingRepository;
import com.hardware.erp.discovery.service.OwnerNotificationService;
import com.hardware.erp.discovery.service.ShopDiscoveryService;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.substitute.entity.ProductRequestRecord;
import com.hardware.erp.product.substitute.repository.ProductRequestRepository;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.subscription.entity.FeatureKey;
import com.hardware.erp.subscription.service.FeatureAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CR-090. The privacy rules this class enforces beyond what the SQL
 * already does:
 *
 *  - Reciprocity: a shop searches the network only if it is in it
 *    (discovery_enabled) - no free-riding on other shops' consent.
 *  - The requesting shop's own product-request customer details are
 *    never part of the search or the notification. What crosses the
 *    tenant boundary is the product's code/model/name and a quantity
 *    bucket, nothing about who asked.
 *  - Consent changes are audited with before/after values, so "I never
 *    turned that on" is answerable from activity_log.
 */
@Service
@RequiredArgsConstructor
public class ShopDiscoveryServiceImpl implements ShopDiscoveryService {

    private static final String MODULE = "DISCOVERY";
    private static final int MAX_NEARBY_RESULTS = 10;

    private final ShopDiscoverySettingRepository settingRepository;
    private final ShopDiscoveryRepository discoveryRepository;
    private final ProductRequestDiscoveryMatchRepository matchRepository;
    private final ProductRequestRepository productRequestRepository;
    private final OwnerNotificationService notificationService;
    private final FeatureAccessService featureAccessService;
    private final ActivityLogService activityLog;

    @Override
    @Transactional(readOnly = true)
    public DiscoverySettingResponse settings() {
        return toResponse(settingFor(SecurityUtils.requireCurrentTenantId()));
    }

    @Override
    @Transactional
    public DiscoverySettingResponse updateSettings(DiscoverySettingRequest request) {
        featureAccessService.requireFeature(FeatureKey.NEARBY_PRODUCT_DISCOVERY);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        ShopDiscoverySetting setting = settingFor(tenantId);
        Map<String, Object> before = snapshot(setting);

        boolean hasLocation = request.latitude() != null && request.longitude() != null;
        if (request.discoveryEnabled() && !hasLocation) {
            throw new BusinessException("Set your shop's location before enabling discovery - other shops are "
                    + "found by distance from it.");
        }

        setting.setDiscoveryEnabled(request.discoveryEnabled());
        // The sub-permissions mean nothing while discovery is off, and
        // leaving them true would silently re-share the moment it is turned
        // back on. Off means everything off.
        boolean on = request.discoveryEnabled();
        setting.setShareShopName(on && request.shareShopName());
        setting.setSharePhone(on && request.sharePhone());
        setting.setShareApproximateLocation(on && request.shareApproximateLocation());
        setting.setShareAvailability(on && request.shareAvailability());
        setting.setLatitude(request.latitude());
        setting.setLongitude(request.longitude());
        setting.setSearchRadiusKm(request.searchRadiusKm());
        setting.setUpdatedAt(LocalDateTime.now());
        setting.setUpdatedBy(SecurityUtils.currentUserId().orElse(null));
        ShopDiscoverySetting saved = settingRepository.save(setting);

        activityLog.updated(MODULE, "DISCOVERY_SETTING", tenantId, "Product discovery sharing",
                before, snapshot(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public NearbyAvailabilityResponse discover(Long productRequestId) {
        featureAccessService.requireFeature(FeatureKey.NEARBY_PRODUCT_DISCOVERY);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        ProductRequestRecord request = requireRequest(productRequestId, tenantId);
        ShopDiscoverySetting mine = settingFor(tenantId);

        String blocked = whySearchUnavailable(mine);
        if (blocked != null) {
            return new NearbyAvailabilityResponse(request.getId(),
                    request.getRequestedProduct().getProductName(), true, blocked, List.of());
        }

        Product product = request.getRequestedProduct();
        List<NearbyMatch> found = discoveryRepository.findNearby(tenantId,
                product.getProductCode(), blankToNull(product.getModelNo()),
                blankToNull(product.getManufacturerCode()), product.getProductName(),
                request.getRequestedQuantity(), MAX_NEARBY_RESULTS);

        matchRepository.deleteForRequest(request.getId());
        matchRepository.flush();
        List<ProductRequestDiscoveryMatch> saved = new ArrayList<>(found.size());
        for (NearbyMatch match : found) {
            saved.add(matchRepository.save(ProductRequestDiscoveryMatch.builder()
                    .productRequestId(request.getId())
                    .sourceTenantId(match.sourceTenantId())
                    .matchedProductName(match.matchedProductName())
                    .availability(match.availability())
                    .distanceKm(match.distanceKm())
                    .shopName(match.shopName())
                    .phone(match.phone())
                    .createdAt(LocalDateTime.now())
                    .build()));
        }

        if (!saved.isEmpty()) {
            // The notification names the product and the count - never a
            // shop, and never the customer. The owner opens the request to
            // see what each shop permitted.
            notificationService.notify(tenantId, OwnerNotificationType.PRODUCT_DISCOVERY,
                    "Nearby availability found",
                    "Customer requested " + product.getProductName() + ". Your current stock: "
                            + request.getRequestedQuantity().stripTrailingZeros().toPlainString()
                            + " requested, none on hand. " + saved.size() + " nearby participating shop"
                            + (saved.size() == 1 ? "" : "s") + " may have it.",
                    "PRODUCT_REQUEST", request.getId());
        }

        activityLog.action(MODULE, "PRODUCT_REQUEST", request.getId(), product.getProductName(),
                com.hardware.erp.common.activity.ActivityAction.UPDATE,
                "Nearby discovery run - " + saved.size() + " match(es)");

        return toNearbyResponse(request, saved, false, null);
    }

    @Override
    @Transactional(readOnly = true)
    public NearbyAvailabilityResponse nearby(Long productRequestId) {
        featureAccessService.requireFeature(FeatureKey.NEARBY_PRODUCT_DISCOVERY);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        ProductRequestRecord request = requireRequest(productRequestId, tenantId);
        String blocked = whySearchUnavailable(settingFor(tenantId));
        return toNearbyResponse(request,
                matchRepository.findByProductRequestIdOrderByDistanceKmAscIdAsc(request.getId()),
                blocked != null, blocked);
    }

    // ---------------------------------------------------------------

    private String whySearchUnavailable(ShopDiscoverySetting mine) {
        if (!mine.isDiscoveryEnabled()) {
            return "Turn on Product discovery sharing in Shop settings to search nearby shops - "
                    + "the network is only open to shops that take part in it.";
        }
        if (!mine.hasLocation()) {
            return "Set your shop's location in Shop settings - nearby shops are found by distance from it.";
        }
        return null;
    }

    private ShopDiscoverySetting settingFor(Long tenantId) {
        return settingRepository.findById(tenantId).orElseGet(() -> ShopDiscoverySetting.defaults(tenantId));
    }

    private ProductRequestRecord requireRequest(Long id, Long tenantId) {
        return productRequestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product request", id));
    }

    private NearbyAvailabilityResponse toNearbyResponse(ProductRequestRecord request,
                                                        List<ProductRequestDiscoveryMatch> matches,
                                                        boolean searchUnavailable, String reason) {
        List<NearbyShopResponse> shops = matches.stream()
                .map(match -> new NearbyShopResponse(
                        match.getId(),
                        match.getShopName(),
                        match.getPhone(),
                        whatsappUrl(match.getPhone()),
                        match.getDistanceKm(),
                        match.getMatchedProductName(),
                        match.getAvailability(),
                        match.getCreatedAt()))
                .toList();
        return new NearbyAvailabilityResponse(request.getId(), request.getRequestedProduct().getProductName(),
                searchUnavailable, reason, shops);
    }

    /** A bare chat link - no message is pre-filled, so nothing about the customer can leak through it (spec's contact flow). */
    private String whatsappUrl(String phone) {
        if (phone == null || !PhoneNumberNormalizer.isValid(phone)) {
            return null;
        }
        return "https://wa.me/" + PhoneNumberNormalizer.toE164Digits(phone);
    }

    private DiscoverySettingResponse toResponse(ShopDiscoverySetting setting) {
        return new DiscoverySettingResponse(
                setting.isDiscoveryEnabled(), setting.isShareShopName(), setting.isSharePhone(),
                setting.isShareApproximateLocation(), setting.isShareAvailability(),
                setting.getLatitude(), setting.getLongitude(), setting.getSearchRadiusKm(),
                setting.getUpdatedAt());
    }

    private Map<String, Object> snapshot(ShopDiscoverySetting setting) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("discoveryEnabled", setting.isDiscoveryEnabled());
        values.put("shareShopName", setting.isShareShopName());
        values.put("sharePhone", setting.isSharePhone());
        values.put("shareApproximateLocation", setting.isShareApproximateLocation());
        values.put("shareAvailability", setting.isShareAvailability());
        values.put("hasLocation", setting.hasLocation());
        values.put("searchRadiusKm", setting.getSearchRadiusKm());
        return values;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
