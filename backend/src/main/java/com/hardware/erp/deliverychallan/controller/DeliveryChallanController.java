package com.hardware.erp.deliverychallan.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanRequest;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanResponse;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanSummaryResponse;
import com.hardware.erp.deliverychallan.entity.DeliveryChallanStatus;
import com.hardware.erp.deliverychallan.service.DeliveryChallanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/delivery-challans")
@RequiredArgsConstructor
@Tag(name = "Delivery Challans")
public class DeliveryChallanController {

    private final DeliveryChallanService deliveryChallanService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).DELIVERY_CHALLAN_MANAGE)")
    public ApiResponse<DeliveryChallanResponse> create(
            @Valid @RequestBody DeliveryChallanRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(deliveryChallanService.create(request, idempotencyKey));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).DELIVERY_CHALLAN_VIEW)")
    public ApiResponse<PageResponse<DeliveryChallanSummaryResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) DeliveryChallanStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(deliveryChallanService.search(search, status, fromDate, toDate, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).DELIVERY_CHALLAN_VIEW)")
    public ApiResponse<DeliveryChallanResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(deliveryChallanService.get(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).DELIVERY_CHALLAN_MANAGE)")
    public ApiResponse<DeliveryChallanResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(deliveryChallanService.cancel(id));
    }

    @PostMapping("/{id}/convert-to-invoice")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_CREATE)")
    public ApiResponse<DeliveryChallanResponse> convertToInvoice(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(deliveryChallanService.convertToInvoice(id, idempotencyKey));
    }
}
