package com.hardware.erp.invoice.dto;

import com.hardware.erp.invoice.entity.InvoiceStatus;

import java.time.LocalDate;

public record InvoiceSummaryResponse(
        Long id,
        String invoiceNumber,
        String customerName,
        String customerMobile,
        LocalDate invoiceDate,
        String totalDisplay,
        String balanceDisplay,
        InvoiceStatus status
) {}
