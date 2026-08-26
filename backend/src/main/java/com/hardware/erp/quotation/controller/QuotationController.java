package com.hardware.erp.quotation.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.quotation.dto.QuotationRequest;
import com.hardware.erp.quotation.dto.QuotationResponse;
import com.hardware.erp.quotation.dto.QuotationStatusRequest;
import com.hardware.erp.quotation.dto.QuotationSummaryResponse;
import com.hardware.erp.quotation.entity.QuotationStatus;
import com.hardware.erp.quotation.service.QuotationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/quotations")
@RequiredArgsConstructor
@Tag(name = "Quotations")
public class QuotationController {

    private final QuotationService quotationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).QUOTATION_MANAGE)")
    public ApiResponse<QuotationResponse> create(@Valid @RequestBody QuotationRequest request) {
        return ApiResponse.ok(quotationService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).QUOTATION_VIEW)")
    public ApiResponse<PageResponse<QuotationSummaryResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) QuotationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(quotationService.search(search, status, fromDate, toDate, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).QUOTATION_VIEW)")
    public ApiResponse<QuotationResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(quotationService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).QUOTATION_MANAGE)")
    public ApiResponse<QuotationResponse> update(
            @PathVariable Long id, @Valid @RequestBody QuotationRequest request) {
        return ApiResponse.ok(quotationService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).QUOTATION_MANAGE)")
    public ApiResponse<QuotationResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody QuotationStatusRequest request) {
        return ApiResponse.ok(quotationService.updateStatus(id, request.status()));
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_CREATE)")
    public ApiResponse<QuotationResponse> convert(@PathVariable Long id) {
        return ApiResponse.ok(quotationService.convert(id));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).QUOTATION_VIEW)")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = quotationService.generatePdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"quotation-" + id + ".pdf\"")
                .body(pdf);
    }
}
