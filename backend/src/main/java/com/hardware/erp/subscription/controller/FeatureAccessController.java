package com.hardware.erp.subscription.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.subscription.dto.FeatureAccessResponse;
import com.hardware.erp.subscription.entity.FeatureKey;
import com.hardware.erp.subscription.service.FeatureAccessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CR-088 §24. Lets the frontend ask "can I use this?" before rendering a
 * feature, so the upgrade dialog can be shown proactively rather than only
 * after a 403. This is UX only - every real write still goes through
 * FeatureAccessService.requireFeature() on its own controller.
 */
@RestController
@RequestMapping("/v1/features")
@RequiredArgsConstructor
@Tag(name = "Subscriptions")
public class FeatureAccessController {

    private final FeatureAccessService featureAccessService;

    @GetMapping("/{featureKey}/access")
    public ApiResponse<FeatureAccessResponse> access(@PathVariable FeatureKey featureKey) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return ApiResponse.ok(featureAccessService.access(tenantId, featureKey));
    }
}
