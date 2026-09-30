package com.hardware.erp.discovery.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.discovery.dto.DiscoveryDtos.NearbyAvailabilityResponse;
import com.hardware.erp.discovery.service.ShopDiscoveryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CR-090. Hangs off the product request (CR-089) because that is the
 * moment "we do not have it" is known. Owner/staff only - PRODUCT_REQUEST_*
 * permissions plus the NEARBY_PRODUCT_DISCOVERY plan gate in the service.
 * There is no customer-facing counterpart to any of this.
 */
@RestController
@RequestMapping("/v1/product-requests/{id}")
@RequiredArgsConstructor
@Tag(name = "Nearby Discovery")
public class NearbyDiscoveryController {

    private final ShopDiscoveryService service;

    @PostMapping("/discover")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_REQUEST_MANAGE)")
    public ApiResponse<NearbyAvailabilityResponse> discover(@PathVariable Long id) {
        return ApiResponse.ok(service.discover(id));
    }

    @GetMapping("/nearby")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_REQUEST_VIEW)")
    public ApiResponse<NearbyAvailabilityResponse> nearby(@PathVariable Long id) {
        return ApiResponse.ok(service.nearby(id));
    }
}
