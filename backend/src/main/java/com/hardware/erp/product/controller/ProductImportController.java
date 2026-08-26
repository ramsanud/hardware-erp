package com.hardware.erp.product.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.product.dto.ProductImportConfirmRequest;
import com.hardware.erp.product.dto.ProductImportPreviewResponse;
import com.hardware.erp.product.dto.ProductImportResultResponse;
import com.hardware.erp.product.service.ProductImportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk product upload (CR-036) - /preview parses and matches without
 * writing anything; /confirm is the only endpoint that persists, one
 * transaction, all-or-nothing. Mirrors PurchaseImportController's design.
 */
@RestController
@RequestMapping("/v1/products/import")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductImportController {

    private final ProductImportService productImportService;

    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    public ApiResponse<ProductImportPreviewResponse> preview(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(productImportService.preview(file));
    }

    @PostMapping("/confirm")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    public ApiResponse<ProductImportResultResponse> confirm(@Valid @RequestBody ProductImportConfirmRequest request) {
        return ApiResponse.ok(productImportService.confirm(request));
    }
}
