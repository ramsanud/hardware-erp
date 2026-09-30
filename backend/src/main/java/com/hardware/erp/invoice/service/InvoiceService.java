package com.hardware.erp.invoice.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.dto.InvoiceSummaryResponse;
import com.hardware.erp.invoice.dto.PaymentRequest;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import com.hardware.erp.notification.entity.NotificationStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface InvoiceService {

    InvoiceResponse create(InvoiceRequest request);

    /**
     * CR-102. Same as the one-argument form, run exactly once per
     * {@code Idempotency-Key}: a retry after a lost response returns the
     * stored result instead of a second invoice. A null or blank key means
     * "no key" and the call simply runs. See IdempotencyService.
     */
    InvoiceResponse create(InvoiceRequest request, String idempotencyKey);

    /**
     * Amends an unpaid invoice in place, keeping its number and date. Refused
     * once any payment exists - see the impl for why.
     */
    InvoiceResponse update(Long id, InvoiceRequest request);

    InvoiceResponse get(Long id);

    PageResponse<InvoiceSummaryResponse> search(String search, InvoiceStatus status,
                                                 LocalDate fromDate, LocalDate toDate, Pageable pageable);

    InvoiceResponse addPayment(Long invoiceId, PaymentRequest request);

    /**
     * CR-102. Same as the one-argument form, run exactly once per
     * {@code Idempotency-Key}: a retry after a lost response returns the
     * stored result instead of a second payment. A null or blank key means
     * "no key" and the call simply runs. See IdempotencyService.
     */
    InvoiceResponse addPayment(Long invoiceId, PaymentRequest request, String idempotencyKey);

    /** CR-091 Phase 3 - reason mandatory; who and when are recorded. */
    InvoiceResponse cancel(Long id, com.hardware.erp.invoice.dto.InvoiceCancelRequest request);

    byte[] generatePdf(Long id);

    /** Task 05 (WhatsApp reminders, MUST-HAVE). Synchronous - see NotificationService.notifyPaymentDue. */
    NotificationStatus sendPaymentReminder(Long id);

    /** CR-056 §8 - manual resend of the invoice-created message over WhatsApp. */
    NotificationStatus sendInvoiceViaWhatsApp(Long id);

    /** CR-056 §10 - manual only. paymentId must belong to this invoice. */
    NotificationStatus sendPaymentReceiptViaWhatsApp(Long invoiceId, Long paymentId);
}
