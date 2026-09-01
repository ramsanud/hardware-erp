package com.hardware.erp.creditnote.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * No customer fields, unlike Invoice/Quotation/Sales Order - the customer
 * is derived from the invoice being returned against, never re-entered.
 */
public record CreditNoteRequest(
        @NotNull Long invoiceId,
        @NotEmpty @Valid List<CreditNoteItemRequest> items,
        @NotBlank(message = "A reason is required for a credit note") @Size(max = 500) String reason,
        @Size(max = 500) String remarks
) {}
