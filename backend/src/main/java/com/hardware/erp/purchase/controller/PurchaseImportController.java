package com.hardware.erp.purchase.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.purchase.dto.ImportConfirmRequest;
import com.hardware.erp.purchase.dto.ImportPreviewResponse;
import com.hardware.erp.purchase.dto.ImportResultResponse;
import com.hardware.erp.purchase.service.PurchaseImportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Two-step upload, matching spec §6/§45: /preview parses and returns
 * structured data without writing anything; /confirm is the only
 * endpoint in this module that persists. Both are gated by
 * PURCHASE_MANAGE, not a new PURCHASE_DOCUMENT_IMPORT permission -
 * importing a bill is exactly "create a purchase," the same authority
 * a manually-entered one already needs (audited against the existing
 * catalogue before adding anything new, per CLAUDE.md's naming law).
 */
@RestController
@RequestMapping("/v1/purchases/import")
@RequiredArgsConstructor
@Tag(name = "Purchases")
public class PurchaseImportController {

    private final PurchaseImportService purchaseImportService;

    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PURCHASE_MANAGE)")
    public ApiResponse<ImportPreviewResponse> preview(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(purchaseImportService.preview(file));
    }

    @PostMapping(value = "/confirm", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PURCHASE_MANAGE)")
    public ApiResponse<ImportResultResponse> confirm(@RequestParam("file") MultipartFile file,
                                                       @Valid @RequestPart("request") ImportConfirmRequest request) {
        return ApiResponse.ok(purchaseImportService.confirm(request, file));
    }
}
