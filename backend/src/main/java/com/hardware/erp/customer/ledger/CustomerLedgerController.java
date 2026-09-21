package com.hardware.erp.customer.ledger;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.customer.ledger.LedgerDtos.AgeingResponse;
import com.hardware.erp.customer.ledger.LedgerDtos.LedgerAdjustmentRequest;
import com.hardware.erp.customer.ledger.LedgerDtos.LedgerBalanceResponse;
import com.hardware.erp.customer.ledger.LedgerDtos.StatementResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * CR-091 Phase 4. Under /v1/customers/{id}/ledger rather than a top-level
 * path - this is one customer's own account, not a cross-customer report
 * (Reports/CR-086 owns the receivables-ageing report across every
 * customer). CUSTOMER_VIEW to read, PAYMENT_MANAGE to adjust - correcting
 * a balance is a money action, the same authority as recording a payment.
 */
@RestController
@RequestMapping("/v1/customers/{customerId}/ledger")
@RequiredArgsConstructor
@Tag(name = "Customer Ledger")
public class CustomerLedgerController {

    private final CustomerLedgerService ledgerService;

    @GetMapping("/balance")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_VIEW)")
    public ApiResponse<LedgerBalanceResponse> balance(@PathVariable Long customerId) {
        return ApiResponse.ok(ledgerService.balance(customerId));
    }

    @GetMapping("/statement")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_VIEW)")
    public ApiResponse<StatementResponse> statement(
            @PathVariable Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(ledgerService.statement(customerId, from, to));
    }

    @GetMapping("/ageing")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_VIEW)")
    public ApiResponse<AgeingResponse> ageing(@PathVariable Long customerId) {
        return ApiResponse.ok(ledgerService.ageing(customerId));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PAYMENT_MANAGE)")
    public ApiResponse<LedgerBalanceResponse> adjust(@PathVariable Long customerId,
                                                     @Valid @RequestBody LedgerAdjustmentRequest request) {
        return ApiResponse.ok("Adjustment recorded", ledgerService.adjust(customerId, request));
    }
}
