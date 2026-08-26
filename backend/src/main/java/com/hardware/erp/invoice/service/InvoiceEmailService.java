package com.hardware.erp.invoice.service;

import com.hardware.erp.notification.entity.NotificationStatus;

/** Backs the invoice detail page's "Email" share action (CR-036) - a real PDF attachment, distinct from NotificationService's text-only invoice-created message. */
public interface InvoiceEmailService {

    NotificationStatus emailInvoicePdf(Long invoiceId, String toEmail);
}
