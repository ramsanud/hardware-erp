package com.hardware.erp.product.substitute.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.CreateRelationshipRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.RelationshipResponse;
import com.hardware.erp.product.substitute.service.SubstituteRecommendationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CR-089 §17. Manually defined alternative/compatible/upgrade mappings on
 * one product.
 *
 * Gated on PRODUCT_MANAGE rather than a new permission: this is product
 * master data, the same authority as editing the product's price or
 * category. Reading them needs only PRODUCT_VIEW, because the mappings are
 * shown on the product detail page counter staff already see.
 *
 * Deliberately NOT plan-gated. A shop on Basic can still record "this
 * replaces that" about its own catalogue - what PREMIUM buys is the engine
 * that uses those mappings automatically (SMART_SUBSTITUTE), not the right
 * to describe your own products.
 */
@RestController
@RequestMapping("/v1/products/{productId}/alternative-mappings")
@RequiredArgsConstructor
@Tag(name = "Smart Substitute")
public class ProductRelationshipController {

    private final SubstituteRecommendationService service;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_VIEW)")
    public ApiResponse<List<RelationshipResponse>> list(@PathVariable Long productId) {
        return ApiResponse.ok(service.relationshipsFor(productId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    public ResponseEntity<ApiResponse<RelationshipResponse>> create(
            @PathVariable Long productId, @Valid @RequestBody CreateRelationshipRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Mapping saved", service.createRelationship(productId, request)));
    }

    @DeleteMapping("/{relationshipId}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    public ApiResponse<Void> delete(@PathVariable Long productId, @PathVariable Long relationshipId) {
        service.deleteRelationship(productId, relationshipId);
        return ApiResponse.message("Mapping removed");
    }
}
