package com.hardware.erp.invoice.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.invoice.dto.EmailInvoiceRequest;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.dto.InvoiceSummaryResponse;
import com.hardware.erp.invoice.dto.PaymentRequest;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import com.hardware.erp.invoice.service.InvoiceEmailService;
import com.hardware.erp.invoice.service.InvoiceService;
import com.hardware.erp.notification.entity.NotificationStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceEmailService invoiceEmailService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_CREATE)")
    public ApiResponse<InvoiceResponse> create(@Valid @RequestBody InvoiceRequest request) {
        return ApiResponse.ok(invoiceService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_VIEW)")
    public ApiResponse<PageResponse<InvoiceSummaryResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(invoiceService.search(search, status, fromDate, toDate, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_VIEW)")
    public ApiResponse<InvoiceResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(invoiceService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_CREATE)")
    public ApiResponse<InvoiceResponse> update(
            @PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
        return ApiResponse.ok(invoiceService.update(id, request));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PAYMENT_MANAGE)")
    public ApiResponse<InvoiceResponse> addPayment(
            @PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return ApiResponse.ok(invoiceService.addPayment(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_CANCEL)")
    public ApiResponse<InvoiceResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(invoiceService.cancel(id));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_VIEW)")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = invoiceService.generatePdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"invoice-" + id + ".pdf\"")
                .body(pdf);
    }

    /** CR-036 - real SMTP send with the actual PDF attached; LOGGED_ONLY (not an error) when no SMTP account is configured in this environment, same fallback SmtpMailService already uses. */
    @PostMapping("/{id}/share/email")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_VIEW)")
    public ApiResponse<NotificationStatus> emailInvoice(
            @PathVariable Long id, @Valid @RequestBody EmailInvoiceRequest request) {
        return ApiResponse.ok(invoiceEmailService.emailInvoicePdf(id, request.toEmail()));
    }

    /** Task 05 (WhatsApp reminders, MUST-HAVE). Synchronous - the caller sees the real Sent/Logged-only status, never an assumed one. */
    @PostMapping("/{id}/remind")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_VIEW)")
    public ApiResponse<NotificationStatus> sendPaymentReminder(@PathVariable Long id) {
        return ApiResponse.ok(invoiceService.sendPaymentReminder(id));
    }

    /** CR-056 §8 - the Invoice detail page's "Send WhatsApp" action, distinct from the automatic on-create send. */
    @PostMapping("/{id}/share/whatsapp")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_VIEW)")
    public ApiResponse<NotificationStatus> sendInvoiceViaWhatsApp(@PathVariable Long id) {
        return ApiResponse.ok(invoiceService.sendInvoiceViaWhatsApp(id));
    }

    /** CR-056 §10 - manual "Send WhatsApp Receipt" for one recorded payment. */
    @PostMapping("/{id}/payments/{paymentId}/share/whatsapp")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PAYMENT_MANAGE)")
    public ApiResponse<NotificationStatus> sendPaymentReceiptViaWhatsApp(
            @PathVariable Long id, @PathVariable Long paymentId) {
        return ApiResponse.ok(invoiceService.sendPaymentReceiptViaWhatsApp(id, paymentId));
    }
}
