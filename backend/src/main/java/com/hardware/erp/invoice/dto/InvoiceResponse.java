package com.hardware.erp.invoice.dto;

import com.hardware.erp.invoice.entity.InvoiceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        Long customerId,
        String customerName,
        String customerMobile,
        LocalDate invoiceDate,
        String subtotalDisplay,
        String gstAmountDisplay,
        String totalDisplay,
        String couponCode,
        String discountDisplay,
        String paidDisplay,
        String balanceDisplay,
        InvoiceStatus status,
        String remarks,
        String transportMode,
        String vehicleNumber,
        String deliveryAddress,
        List<InvoiceItemResponse> items,
        List<PaymentResponse> payments,
        LocalDateTime createdAt,
        /** Null when this invoice uses the shop's single default bank fields instead of a saved account (CR-036). */
        Long bankAccountId,
        String bankAccountLabel,
        Long bankAccountQrId
) {}
