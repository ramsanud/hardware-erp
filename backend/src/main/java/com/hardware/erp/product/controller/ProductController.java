package com.hardware.erp.product.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.ErrorResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.product.dto.*;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Depends On:
 *   Module 1 - PRODUCT_VIEW, PRODUCT_MANAGE and PRODUCT_VIEW_COST, seeded
 *   by V1. STAFF has PRODUCT_VIEW but not PRODUCT_VIEW_COST, so counter
 *   staff can look products up without ever seeing purchase price.
 */
@RestController
@RequestMapping("/v1/products")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "5. Products", description = "Sellable catalogue items. Module 3.")
public class ProductController {

    private static final int MAX_PAGE_SIZE = 100;

    /** Whitelist: a raw parameter reaching ORDER BY is an injection surface. */
    private static final Map<String, String> SORTABLE = Map.of(
            "productName", "productName",
            "productCode", "productCode",
            "status", "status",
            "createdAt", "createdAt");

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_VIEW)")
    @Operation(
            summary = "Search products",
            description = "Matches product name, code, barcode, manufacturer code or model "
                        + "number - never name alone (FEATURE_REGISTRY Module 6). Page size "
                        + "is clamped to 100 and sort fields are whitelisted.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Missing PRODUCT_VIEW",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> search(
            @Parameter(description = "Matches name, code, barcode, manufacturer code or model number",
                       example = "godrej lock")
            @RequestParam(required = false) String search,
            @Parameter(example = "ACTIVE") @RequestParam(required = false) ProductStatus status,
            @Parameter(example = "3") @RequestParam(required = false) Long categoryId,
            @Parameter(example = "4") @RequestParam(required = false) Long brandId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "productName, productCode, status, createdAt")
            @RequestParam(defaultValue = "productName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String safeSort = SORTABLE.getOrDefault(sortBy, "productName");
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.DESC : Sort.Direction.ASC;

        var pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(direction, safeSort));
        return ResponseEntity.ok(ApiResponse.ok(
                productService.search(search, status, categoryId, brandId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_VIEW)")
    @Operation(
            summary = "Get one product",
            description = "purchasePricePaise/purchasePriceDisplay are null unless the "
                        + "caller holds PRODUCT_VIEW_COST.")
    public ResponseEntity<ApiResponse<ProductResponse>> get(
            @Parameter(example = "42") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.get(id)));
    }

    @GetMapping("/{id}/price-history")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_VIEW)")
    @Operation(
            summary = "Recent invoice lines for this product",
            description = "CR-053 backlog item 1. Up to the 20 most recent non-cancelled sales, "
                        + "newest first. Gated on the frontend by Tenant.showPriceHistory - this "
                        + "endpoint itself only requires PRODUCT_VIEW.")
    public ResponseEntity<ApiResponse<java.util.List<ProductPriceHistoryResponse>>> priceHistory(
            @Parameter(example = "42") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.priceHistory(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    @Operation(
            summary = "Create a product",
            description = "Leave `productCode` blank and the system generates the next PRD-nnnnnn.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "Created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Duplicate code, name or barcode",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Product created", productService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    @Operation(summary = "Update a product")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Product updated",
                productService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    @Operation(
            summary = "Deactivate a product (soft delete)",
            description = "The row is never removed. Future purchase and invoice documents "
                        + "reference product_id permanently.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.softDelete(id, SecurityUtils.requireCurrentUser().getId());
        return ResponseEntity.noContent().build();
    }
}
