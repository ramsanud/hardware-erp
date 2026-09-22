package com.hardware.erp.branch.controller;

import com.hardware.erp.branch.dto.BranchDtos.AssignUserBranchRequest;
import com.hardware.erp.branch.dto.BranchDtos.BranchRequest;
import com.hardware.erp.branch.dto.BranchDtos.BranchResponse;
import com.hardware.erp.branch.dto.BranchDtos.BranchStockResponse;
import com.hardware.erp.branch.dto.BranchDtos.BranchSummaryResponse;
import com.hardware.erp.branch.dto.BranchDtos.StockTransferRequest;
import com.hardware.erp.branch.dto.BranchDtos.StockTransferResponse;
import com.hardware.erp.branch.service.BranchService;
import com.hardware.erp.branch.service.StockTransferService;
import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
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
import java.util.List;

/**
 * CR-092. Every branch id in a path is resolved with findByIdAndTenantId;
 * the branch a document is stamped with is never taken from a request.
 */
@RestController
@RequestMapping("/v1/branches")
@RequiredArgsConstructor
@Tag(name = "Branches")
public class BranchController {

    private final BranchService branchService;
    private final StockTransferService stockTransferService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).BRANCH_VIEW)")
    public ApiResponse<List<BranchResponse>> list() {
        return ApiResponse.ok(branchService.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).BRANCH_MANAGE)")
    public ApiResponse<BranchResponse> create(@Valid @RequestBody BranchRequest request) {
        return ApiResponse.ok("Branch created", branchService.create(request));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).BRANCH_VIEW)")
    public ApiResponse<List<BranchSummaryResponse>> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(branchService.summary(from, to));
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).BRANCH_VIEW)")
    public ApiResponse<List<BranchStockResponse>> stock(@RequestParam(required = false) Long branchId,
                                                        @RequestParam(required = false) String search) {
        return ApiResponse.ok(branchService.stock(branchId, search));
    }

    @GetMapping("/transfers")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).BRANCH_VIEW)")
    public ApiResponse<PageResponse<StockTransferResponse>> transfers(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(stockTransferService.list(pageable));
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).STOCK_TRANSFER_MANAGE)")
    public ApiResponse<StockTransferResponse> transfer(@Valid @RequestBody StockTransferRequest request) {
        return ApiResponse.ok("Stock transferred", stockTransferService.create(request));
    }

    @GetMapping("/transfers/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).BRANCH_VIEW)")
    public ApiResponse<StockTransferResponse> transfer(@PathVariable Long id) {
        return ApiResponse.ok(stockTransferService.get(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).BRANCH_VIEW)")
    public ApiResponse<BranchResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(branchService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).BRANCH_MANAGE)")
    public ApiResponse<BranchResponse> update(@PathVariable Long id, @Valid @RequestBody BranchRequest request) {
        return ApiResponse.ok("Branch updated", branchService.update(id, request));
    }

    /** Assigns a user to a branch (or clears it). BRANCH_MANAGE, because it changes which branch that user's documents land on. */
    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).BRANCH_MANAGE)")
    public ApiResponse<BranchResponse> assignUser(@PathVariable Long userId, @Valid @RequestBody AssignUserBranchRequest request) {
        return ApiResponse.ok("User's branch updated", branchService.assignUser(userId, request));
    }
}
