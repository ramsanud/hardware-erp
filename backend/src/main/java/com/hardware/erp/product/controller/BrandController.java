package com.hardware.erp.product.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.product.dto.BrandRequest;
import com.hardware.erp.product.dto.BrandResponse;
import com.hardware.erp.product.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/brands")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "4. Brands", description = "Brand master. Module 3.")
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_VIEW)")
    @Operation(summary = "List all brands",
            description = "Not paginated - a shop's brand list is small enough to render whole.")
    public ResponseEntity<ApiResponse<List<BrandResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(brandService.findAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_VIEW)")
    @Operation(summary = "Get one brand")
    public ResponseEntity<ApiResponse<BrandResponse>> get(
            @Parameter(example = "4") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(brandService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    @Operation(summary = "Create a brand",
            description = "Leave `brandCode` blank and the system generates the next BRD-nnnn.")
    public ResponseEntity<ApiResponse<BrandResponse>> create(
            @Valid @RequestBody BrandRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Brand created", brandService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    @Operation(summary = "Update a brand")
    public ResponseEntity<ApiResponse<BrandResponse>> update(
            @PathVariable Long id, @Valid @RequestBody BrandRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Brand updated", brandService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    @Operation(
            summary = "Delete a brand",
            description = "Hard delete. Rejected with 400 if any product still references it.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        brandService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
