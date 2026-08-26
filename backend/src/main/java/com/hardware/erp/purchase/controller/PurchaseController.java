package com.hardware.erp.purchase.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.purchase.dto.PurchaseRequest;
import com.hardware.erp.purchase.dto.PurchaseResponse;
import com.hardware.erp.purchase.dto.PurchaseSummaryResponse;
import com.hardware.erp.purchase.dto.RecordPurchasePaymentRequest;
import com.hardware.erp.purchase.entity.PurchaseDocument;
import com.hardware.erp.purchase.entity.PurchaseStatus;
import com.hardware.erp.purchase.service.PurchaseService;
import com.hardware.erp.purchase.upload.DocumentUploadValidation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/purchases")
@RequiredArgsConstructor
@Tag(name = "Purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PURCHASE_MANAGE)")
    public ApiResponse<PurchaseResponse> create(@Valid @RequestBody PurchaseRequest request) {
        return ApiResponse.ok(purchaseService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PURCHASE_VIEW)")
    public ApiResponse<PageResponse<PurchaseSummaryResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PurchaseStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(purchaseService.search(search, status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PURCHASE_VIEW)")
    public ApiResponse<PurchaseResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(purchaseService.get(id));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PURCHASE_MANAGE)")
    public ApiResponse<PurchaseResponse> addPayment(
            @PathVariable Long id, @Valid @RequestBody RecordPurchasePaymentRequest request) {
        return ApiResponse.ok(purchaseService.addPayment(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PURCHASE_MANAGE)")
    public ApiResponse<PurchaseResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(purchaseService.cancel(id));
    }

    @GetMapping("/{id}/document")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PURCHASE_VIEW)")
    public ResponseEntity<byte[]> document(@PathVariable Long id) {
        PurchaseDocument document = purchaseService.getDocument(id);
        // Never trust the stored contentType column for what gets served - it was populated from a
        // client-declared multipart header at upload time and a "bill.csv" declared text/html would
        // render as live HTML (stored XSS) here. Derive it from the filename's validated extension instead.
        String filename = document.getOriginalFilename();
        int dot = filename.lastIndexOf('.');
        String extension = dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DocumentUploadValidation.safeContentType(extension)))
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .body(document.getFileData());
    }
}
