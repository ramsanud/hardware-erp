package com.hardware.erp.invoice.dto;

import com.hardware.erp.invoice.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * initialPaymentPaise is optional (CR-021 / your invoice spec). Absent or
 * zero leaves the invoice UNPAID. paymentMethod is required only when an
 * initial payment is given.
 */
public record InvoiceRequest(
        @NotBlank @Size(max = 255) String customerName,
        @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a valid 10-digit mobile number")
        String customerMobile,
        @Email @Size(max = 255) String customerEmail,
        @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
                message = "Enter a valid 15-character GSTIN")
        String customerGstNo,
        @Pattern(regexp = "^[0-9]{2}$", message = "State code is the 2-digit GST state code")
        String customerStateCode,
        @NotEmpty @Valid List<InvoiceItemRequest> items,
        @PositiveOrZero Long initialPaymentPaise,
        PaymentMethod paymentMethod,
        @Size(max = 500) String remarks,
        @Size(max = 50) String transportMode,
        @Size(max = 20) String vehicleNumber,
        @Size(max = 500) String deliveryAddress,
        /** Optional (CR-028). Validated and applied server-side - the discount amount is never client-supplied. */
        @Size(max = 30) String couponCode,
        /** Optional (CR-036). Which of the shop's saved bank accounts to print on this invoice - null falls back to the shop's single default bank fields. */
        Long bankAccountId,
        /** Optional (CR-036). Must belong to bankAccountId - validated server-side. Null shows the account's own first QR, or none if it has none. */
        Long bankAccountQrId
) {
    /** Convenience constructor for callers that don't need shipment details (e.g. Quotation convert). */
    public InvoiceRequest(String customerName, String customerMobile, String customerEmail,
                           String customerGstNo, String customerStateCode, List<InvoiceItemRequest> items,
                           Long initialPaymentPaise, PaymentMethod paymentMethod, String remarks) {
        this(customerName, customerMobile, customerEmail, customerGstNo, customerStateCode, items,
                initialPaymentPaise, paymentMethod, remarks, null, null, null, null, null, null);
    }
}
