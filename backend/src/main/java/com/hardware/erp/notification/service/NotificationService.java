package com.hardware.erp.notification.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.entity.Payment;
import com.hardware.erp.notification.dto.NotificationLogResponse;
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

    /** Tenant-scoped, newest first. Backs GET /v1/notifications/log. */
    PageResponse<NotificationLogResponse> search(Pageable pageable);
}
