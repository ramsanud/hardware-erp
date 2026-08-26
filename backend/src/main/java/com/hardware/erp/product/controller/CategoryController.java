package com.hardware.erp.product.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.product.dto.CategoryRequest;
import com.hardware.erp.product.dto.CategoryResponse;
import com.hardware.erp.product.service.CategoryService;
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

/**
 * Depends On:
 *   Module 1 - PRODUCT_VIEW and PRODUCT_MANAGE, seeded by V1. Categories
 *   are a sub-resource of the product catalogue and reuse those two
 *   permissions rather than a permission of their own.
 */
@RestController
@RequestMapping("/v1/categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "3. Categories", description = "Hierarchical product categories. Module 3.")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_VIEW)")
    @Operation(summary = "List all categories",
            description = "Not paginated - a shop's category tree is small enough to render whole.")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.findAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_VIEW)")
    @Operation(summary = "Get one category")
    public ResponseEntity<ApiResponse<CategoryResponse>> get(
            @Parameter(example = "3") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    @Operation(summary = "Create a category",
            description = "Leave `categoryCode` blank and the system generates the next CAT-nnnn.")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Category created", categoryService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    @Operation(summary = "Update a category")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Category updated",
                categoryService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    @Operation(
            summary = "Delete a category",
            description = "Hard delete. Rejected with 400 if any product or sub-category "
                        + "still references it - reassign them first.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
