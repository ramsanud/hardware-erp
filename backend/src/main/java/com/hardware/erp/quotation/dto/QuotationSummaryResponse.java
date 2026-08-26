package com.hardware.erp.quotation.dto;

import com.hardware.erp.quotation.entity.QuotationStatus;

import java.time.LocalDate;

public record QuotationSummaryResponse(
        Long id,
        String quotationNumber,
        String customerName,
        String customerMobile,
        LocalDate quotationDate,
        LocalDate validUntil,
        boolean expired,
        String totalDisplay,
        QuotationStatus status
) {}
