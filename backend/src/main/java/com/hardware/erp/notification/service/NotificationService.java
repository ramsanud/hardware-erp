package com.hardware.erp.notification.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.entity.Payment;
import com.hardware.erp.notification.dto.NotificationLogResponse;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationStatus;
import org.springframework.data.domain.Pageable;

/**
 * Facade: builds the message text, decides which channel(s) to use, and
 * records every attempt to notification_log. Every trigger method runs
 * @Async - a slow or failing SMS/email provider must never sit inside the
 * HTTP response for creating an invoice or recording a payment.
 */
public interface NotificationService {

    void notifyInvoiceCreated(Invoice invoice);

    void notifyPaymentReceived(Invoice invoice, Payment payment);

    /**
     * A tenant reporting a problem with the application itself - distinct
     * from notifyInvoiceCreated/notifyPaymentReceived, which notify a
     * *customer* about *their* business record. This one emails the
     * platform admin (app.support.admin-email), not anyone in the tenant's
     * own data. Runs synchronously (not @Async) - the caller submitting a
     * support request wants to know it actually went through before the
     * dialog closes, unlike a background invoice notification.
     */
    NotificationStatus contactAdmin(String subject, String message);

    /**
     * Task 05 (WhatsApp reminders, MUST-HAVE). Sends a payment-due nudge to
     * the invoice's customer over WhatsApp, synchronously - the caller (the
     * "Send WhatsApp reminder" button, or the future GST/aging screens) sees
     * the real resulting status, never an assumed one. Throws
     * BusinessException for a cancelled/fully-paid invoice, a customer with
     * no mobile number, or a reminder already sent today for this invoice.
     */
    NotificationStatus notifyPaymentDue(Invoice invoice);

    /**
     * CR-056 §8 - resends the invoice-created message over WhatsApp on
     * demand (the "Send WhatsApp" button on the Invoice detail page),
     * distinct from the automatic send notifyInvoiceCreated already does
     * on creation. Synchronous, same reasoning as notifyPaymentDue. Throws
     * BusinessException if the customer has no mobile number or has opted
     * out of WhatsApp.
     */
    NotificationStatus sendInvoiceViaWhatsApp(Invoice invoice);

    /**
     * CR-056 §10 - manual only, never automatic (there is no tenant-level
     * "auto-send receipt" toggle - see the spec's own "do not automatically
     * send unless enabled" instruction, satisfied here by this simply never
     * firing on its own).
     */
    NotificationStatus sendPaymentReceiptViaWhatsApp(Invoice invoice, Payment payment);

    /**
     * CR-056 §3 - the Settings page's "Test WhatsApp" button. Throws
     * BusinessException immediately (never LOGGED_ONLY) if the tenant has
     * no connected WhatsApp Business account - a deliberate test action
     * should never quietly report success when nothing was actually sent.
     */
    NotificationStatus sendTestWhatsApp(String toMobileNo);

    /** Tenant-scoped, newest first. Backs GET /v1/notifications/log. Null channel means every channel. */
    PageResponse<NotificationLogResponse> search(NotificationChannel channel, Pageable pageable);
}
