package com.hardware.erp.product.substitute.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SubstituteSettingRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SubstituteSettingResponse;
import com.hardware.erp.product.substitute.service.SubstituteRecommendationService;
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
 * CR-089 §7/§9. The part of the recommendation policy the owner controls:
 * the minimum score worth showing, whether over-budget alternatives appear
 * at all, and how many to show by default.
 *
 * Its own path rather than /v1/product-requests/settings so that "settings"
 * can never be read as a request id, and its own permission split: anyone
 * who can see the queue may read the policy, only SETTINGS_MANAGE may
 * change it.
 */
@RestController
@RequestMapping("/v1/substitute-settings")
@RequiredArgsConstructor
@Tag(name = "Smart Substitute")
public class SubstituteSettingController {

    private final SubstituteRecommendationService service;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_REQUEST_VIEW)")
    public ApiResponse<SubstituteSettingResponse> get() {
        return ApiResponse.ok(service.settings());
    }

    @PutMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<SubstituteSettingResponse> update(@Valid @RequestBody SubstituteSettingRequest request) {
        return ApiResponse.ok("Substitute settings saved", service.updateSettings(request));
    }
}
