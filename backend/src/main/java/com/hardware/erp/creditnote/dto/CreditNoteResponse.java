package com.hardware.erp.creditnote.dto;

import com.hardware.erp.creditnote.entity.CreditNoteStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CreditNoteResponse(
        Long id,
        String creditNoteNumber,
        Long invoiceId,
        String invoiceNumber,
        Long customerId,
        String customerName,
        String customerMobile,
        LocalDate creditNoteDate,
        String reason,
        String subtotalDisplay,
        String gstAmountDisplay,
        String totalDisplay,
        CreditNoteStatus status,
        String remarks,
        List<CreditNoteItemResponse> items,
        LocalDateTime createdAt
) {}
