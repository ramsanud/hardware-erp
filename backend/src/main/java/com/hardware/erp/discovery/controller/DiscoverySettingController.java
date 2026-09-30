package com.hardware.erp.discovery.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.discovery.dto.DiscoveryDtos.DiscoverySettingRequest;
import com.hardware.erp.discovery.dto.DiscoveryDtos.DiscoverySettingResponse;
import com.hardware.erp.discovery.service.ShopDiscoveryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CR-090. The shop's own consent. Reading it is SETTINGS_VIEW (it is a
 * setting); changing it is SETTINGS_MANAGE - the owner-level authority
 * that also governs the GSTIN, because sharing your shop with strangers
 * is at least that consequential.
 */
@RestController
@RequestMapping("/v1/discovery/settings")
@RequiredArgsConstructor
@Tag(name = "Nearby Discovery")
public class DiscoverySettingController {

    private final ShopDiscoveryService service;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_VIEW)")
    public ApiResponse<DiscoverySettingResponse> get() {
        return ApiResponse.ok(service.settings());
    }

    @PutMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<DiscoverySettingResponse> update(@Valid @RequestBody DiscoverySettingRequest request) {
        return ApiResponse.ok("Discovery sharing updated", service.updateSettings(request));
    }
}
