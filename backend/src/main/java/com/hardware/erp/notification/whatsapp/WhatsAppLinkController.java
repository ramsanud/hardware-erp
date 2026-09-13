package com.hardware.erp.notification.whatsapp;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.notification.dto.WhatsAppLinkResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CR-080 - manual WhatsApp links. Every mapping is a GET with no side effect:
 * nothing is sent, nothing is written, the response is a URL the browser
 * opens. Each one is gated by the same VIEW permission as the document it
 * describes - a person who may read an invoice may also hand its summary to
 * the customer over WhatsApp.
 */
@RestController
@RequestMapping("/v1/whatsapp/links")
@RequiredArgsConstructor
@Tag(name = "WhatsApp")
public class WhatsAppLinkController {

    private final WhatsAppLinkService whatsAppLinkService;

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_VIEW)")
    @Operation(summary = "wa.me link that opens the customer's chat with the invoice summary pre-filled")
    public ApiResponse<WhatsAppLinkResponse> invoice(@PathVariable Long id) {
        return ApiResponse.ok(whatsAppLinkService.forInvoice(id));
    }

    @GetMapping("/invoices/{id}/reminder")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_VIEW)")
    @Operation(summary = "wa.me link with a payment reminder for an invoice that still has a balance")
    public ApiResponse<WhatsAppLinkResponse> paymentReminder(@PathVariable Long id) {
        return ApiResponse.ok(whatsAppLinkService.forPaymentReminder(id));
    }

    @GetMapping("/invoices/{id}/payments/{paymentId}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PAYMENT_VIEW)")
    @Operation(summary = "wa.me link with a receipt for one recorded payment")
    public ApiResponse<WhatsAppLinkResponse> paymentReceipt(@PathVariable Long id, @PathVariable Long paymentId) {
        return ApiResponse.ok(whatsAppLinkService.forPaymentReceipt(id, paymentId));
    }

    @GetMapping("/quotations/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).QUOTATION_VIEW)")
    @Operation(summary = "wa.me link that opens the customer's chat with the quotation summary pre-filled")
    public ApiResponse<WhatsAppLinkResponse> quotation(@PathVariable Long id) {
        return ApiResponse.ok(whatsAppLinkService.forQuotation(id));
    }

    @GetMapping("/customers/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_VIEW)")
    @Operation(summary = "wa.me link that opens the customer's chat with a greeting pre-filled")
    public ApiResponse<WhatsAppLinkResponse> customer(@PathVariable Long id) {
        return ApiResponse.ok(whatsAppLinkService.forCustomer(id));
    }
}
