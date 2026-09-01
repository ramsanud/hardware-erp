package com.hardware.erp.salesorder.controller;

import com.hardware.erp.auth.entity.PermissionCode;
import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.salesorder.dto.SalesOrderRequest;
import com.hardware.erp.salesorder.dto.SalesOrderResponse;
import com.hardware.erp.salesorder.dto.SalesOrderStatusRequest;
import com.hardware.erp.salesorder.dto.SalesOrderSummaryResponse;
import com.hardware.erp.salesorder.entity.SalesOrderStatus;
import com.hardware.erp.salesorder.service.SalesOrderService;
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
@RequestMapping("/v1/sales-orders")
@RequiredArgsConstructor
@Tag(name = "Sales Orders")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SALES_ORDER_MANAGE)")
    public ApiResponse<SalesOrderResponse> create(
            @Valid @RequestBody SalesOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(salesOrderService.create(request, idempotencyKey));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SALES_ORDER_VIEW)")
    public ApiResponse<PageResponse<SalesOrderSummaryResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SalesOrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(salesOrderService.search(search, status, fromDate, toDate, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SALES_ORDER_VIEW)")
    public ApiResponse<SalesOrderResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(salesOrderService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SALES_ORDER_MANAGE)")
    public ApiResponse<SalesOrderResponse> update(@PathVariable Long id, @Valid @RequestBody SalesOrderRequest request) {
        return ApiResponse.ok(salesOrderService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SALES_ORDER_MANAGE)")
    public ApiResponse<SalesOrderResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody SalesOrderStatusRequest request) {
        return ApiResponse.ok(salesOrderService.updateStatus(id, request.status()));
    }

    @PostMapping("/{id}/convert-to-invoice")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_CREATE)")
    public ApiResponse<SalesOrderResponse> convertToInvoice(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(salesOrderService.convertToInvoice(id, idempotencyKey));
    }

    @PostMapping("/{id}/convert-to-delivery-challan")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).DELIVERY_CHALLAN_MANAGE)")
    public ApiResponse<SalesOrderResponse> convertToDeliveryChallan(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(salesOrderService.convertToDeliveryChallan(id, idempotencyKey));
    }
}
