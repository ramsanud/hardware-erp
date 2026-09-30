package com.hardware.erp.customer.ledger;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** CR-091 Phase 4. Every money figure is paise plus a server-formatted display string; the browser formats nothing. */
public final class LedgerDtos {

    private LedgerDtos() {
    }

    @Schema(name = "LedgerBalanceResponse")
    public record LedgerBalanceResponse(
            Long customerId,
            String customerName,
            @Schema(description = "Positive = the customer owes the shop; negative = the customer is in advance") long balancePaise,
            String balanceDisplay,
            long totalDebitPaise,
            String totalDebitDisplay,
            long totalCreditPaise,
            String totalCreditDisplay
    ) {}

    @Schema(name = "LedgerEntryResponse")
    public record LedgerEntryResponse(
            Long id,
            LedgerEntryType entryType,
            LocalDateTime entryDate,
            long debitPaise,
            String debitDisplay,
            long creditPaise,
            String creditDisplay,
            @Schema(description = "Running balance after this row") long balancePaise,
            String balanceDisplay,
            String referenceType,
            Long referenceId,
            String referenceNumber,
            String notes
    ) {}

    @Schema(name = "CustomerStatementResponse")
    public record StatementResponse(
            Long customerId,
            String customerName,
            LocalDate from,
            LocalDate to,
            long openingBalancePaise,
            String openingBalanceDisplay,
            List<LedgerEntryResponse> entries,
            long closingBalancePaise,
            String closingBalanceDisplay
    ) {}

    @Schema(name = "AgeingBucket")
    public record AgeingBucket(String label, int fromDays, Integer toDays, long paise, String display, int invoiceCount) {}

    @Schema(name = "AgeingInvoice")
    public record AgeingInvoice(Long invoiceId, String invoiceNumber, LocalDate invoiceDate, int ageDays,
                                long totalPaise, long outstandingPaise, String outstandingDisplay) {}

    @Schema(name = "CustomerAgeingResponse")
    public record AgeingResponse(
            Long customerId,
            String customerName,
            @Schema(description = "Age is counted from the invoice date - invoices carry no separate due date") LocalDate asOf,
            List<AgeingBucket> buckets,
            List<AgeingInvoice> invoices,
            long totalOutstandingPaise,
            String totalOutstandingDisplay
    ) {}

    @Schema(name = "LedgerAdjustmentRequest")
    public record LedgerAdjustmentRequest(
            @Schema(description = "Positive paise. Direction is `debit`.", example = "50000")
            @NotNull @Min(1) Long amountPaise,
            @Schema(description = "true = the customer owes more; false = the customer owes less") @NotNull Boolean debit,
            @NotBlank(message = "A reason is required for a manual adjustment") @Size(max = 255) String reason
    ) {}
}
