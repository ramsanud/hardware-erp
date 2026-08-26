package com.hardware.erp.quotation.dto;

import com.hardware.erp.quotation.entity.QuotationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record QuotationResponse(
        Long id,
        String quotationNumber,
        Long customerId,
        String customerName,
        String customerMobile,
        LocalDate quotationDate,
        LocalDate validUntil,
        boolean expired,
        String subtotalDisplay,
        String gstAmountDisplay,
        String totalDisplay,
        QuotationStatus status,
        String remarks,
        Long convertedInvoiceId,
        List<QuotationItemResponse> items,
        LocalDateTime createdAt
) {}
