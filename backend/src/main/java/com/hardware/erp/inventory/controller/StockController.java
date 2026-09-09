package com.hardware.erp.inventory.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.inventory.dto.StockAdjustmentRequest;
import com.hardware.erp.inventory.dto.StockMovementResponse;
import com.hardware.erp.inventory.dto.StockResponse;
import com.hardware.erp.inventory.service.StockService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/stock")
@RequiredArgsConstructor
@Tag(name = "Inventory")
public class StockController {

    private final StockService stockService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVENTORY_VIEW)")
    public ApiResponse<PageResponse<StockResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            // CR-070. Out of stock is a different question from low stock: low
            // is a purchasing prompt, zero is a lost sale in progress.
            @RequestParam(defaultValue = "false") boolean outOfStockOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(stockService.search(search, lowStockOnly, outOfStockOnly, pageable));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVENTORY_VIEW)")
    public ApiResponse<StockResponse> get(@PathVariable Long productId) {
        return ApiResponse.ok(stockService.get(productId));
    }

    @GetMapping("/{productId}/movements")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVENTORY_VIEW)")
    public ApiResponse<PageResponse<StockMovementResponse>> movements(
            @PathVariable Long productId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(stockService.movements(productId, pageable));
    }

    @PostMapping("/{productId}/adjust")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVENTORY_ADJUST)")
    public ApiResponse<StockMovementResponse> adjust(
            @PathVariable Long productId, @Valid @RequestBody StockAdjustmentRequest request) {
        return ApiResponse.ok(stockService.adjust(productId, request));
    }
}
