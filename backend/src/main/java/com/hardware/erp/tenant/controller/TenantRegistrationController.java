package com.hardware.erp.tenant.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import com.hardware.erp.tenant.dto.TenantRegistrationResponse;
import com.hardware.erp.tenant.service.TenantRegistrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public - no @PreAuthorize, permitAll() in SecurityConfig, rate-limited in
 * RateLimitFilter (REGISTER_PER_IP). A new shop signing up, not a user
 * joining an existing one - CR-008's "no self-registration" is unaffected.
 */
@RestController
@RequestMapping("/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant Registration")
public class TenantRegistrationController {

    private final TenantRegistrationService tenantRegistrationService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TenantRegistrationResponse> register(@Valid @RequestBody TenantRegistrationRequest request) {
        return ApiResponse.ok(tenantRegistrationService.register(request));
    }

    @GetMapping("/register/slug-available")
    public ApiResponse<Map<String, Boolean>> slugAvailable(@RequestParam String slug) {
        return ApiResponse.ok(Map.of("available", tenantRegistrationService.isSlugAvailable(slug)));
    }
}
