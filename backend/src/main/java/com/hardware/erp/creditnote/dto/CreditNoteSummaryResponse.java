package com.hardware.erp.creditnote.dto;

import com.hardware.erp.creditnote.entity.CreditNoteStatus;

import java.time.LocalDate;

public record CreditNoteSummaryResponse(
        Long id,
        String creditNoteNumber,
        String invoiceNumber,
        String customerName,
        String customerMobile,
        LocalDate creditNoteDate,
        String totalDisplay,
        CreditNoteStatus status
) {}
