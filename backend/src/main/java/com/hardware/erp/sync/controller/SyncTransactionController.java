package com.hardware.erp.sync.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.sync.dto.SyncDtos.SyncBatchRequest;
import com.hardware.erp.sync.dto.SyncDtos.SyncBatchResponse;
import com.hardware.erp.sync.service.SyncTransactionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CR-091 Phase 9. Gated on INVOICE_CREATE - syncing an offline invoice is
 * exactly the authority to create one online. No endpoint here accepts a
 * tenant id; the tenant is the caller's own, from the JWT.
 */
@RestController
@RequestMapping("/v1/sync")
@RequiredArgsConstructor
@Tag(name = "Offline Sync")
public class SyncTransactionController {

    private final SyncTransactionService syncTransactionService;

    @PostMapping("/transactions")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_CREATE)")
    public ApiResponse<SyncBatchResponse> sync(@Valid @RequestBody SyncBatchRequest request) {
        return ApiResponse.ok(syncTransactionService.sync(request));
    }
}
