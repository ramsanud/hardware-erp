package com.hardware.erp.product.substitute.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.ComparisonResponse;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.CreateProductRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.ProductRequestResponse;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SelectAlternativeRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SubstituteSettingRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SubstituteSettingResponse;
import com.hardware.erp.product.substitute.entity.ProductRequestStatus;
import com.hardware.erp.product.substitute.service.SubstituteRecommendationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CR-089 §20. The customer-product-request queue and its alternatives.
 *
 * Two gates apply to every call and both are server-side: the permission
 * (@PreAuthorize) and the plan (FeatureKey.SMART_SUBSTITUTE, checked in the
 * service so an internal caller is gated identically). Nothing here ever
 * accepts a tenant id - the shop comes from the JWT (CR-016).
 */
@RestController
@RequestMapping("/v1/product-requests")
@RequiredArgsConstructor
@Tag(name = "Smart Substitute")
public class ProductRequestController {

    private final SubstituteRecommendationService service;

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_REQUEST_MANAGE)")
    public ResponseEntity<ApiResponse<ProductRequestResponse>> create(
            @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Request recorded", service.createRequest(request)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_REQUEST_VIEW)")
    public ApiResponse<PageResponse<ProductRequestResponse>> search(
            @RequestParam(required = false) ProductRequestStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.search(status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_REQUEST_VIEW)")
    public ApiResponse<ProductRequestResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** §11. The alternatives as stored; recompute when stock or prices have moved since. */
    @GetMapping("/{id}/alternatives")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_REQUEST_VIEW)")
    public ApiResponse<ProductRequestResponse> alternatives(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/{id}/alternatives/recompute")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_REQUEST_MANAGE)")
    public ApiResponse<ProductRequestResponse> recompute(@PathVariable Long id) {
        return ApiResponse.ok("Alternatives recalculated", service.recomputeAlternatives(id));
    }

    /** §12. Requested vs one alternative, attribute by attribute. */
    @GetMapping("/{id}/compare")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_REQUEST_VIEW)")
    public ApiResponse<ComparisonResponse> compare(@PathVariable Long id,
                                                   @RequestParam Long alternativeProductId) {
        return ApiResponse.ok(service.compare(id, alternativeProductId));
    }

    /** §13/§24. The owner chooses - this records the decision and changes no invoice. */
    @PostMapping("/{id}/select-alternative")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_REQUEST_MANAGE)")
    public ApiResponse<ProductRequestResponse> selectAlternative(
            @PathVariable Long id, @Valid @RequestBody SelectAlternativeRequest request) {
        return ApiResponse.ok("Alternative recorded", service.selectAlternative(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_REQUEST_MANAGE)")
    public ApiResponse<ProductRequestResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok("Request cancelled", service.cancel(id));
    }

}
