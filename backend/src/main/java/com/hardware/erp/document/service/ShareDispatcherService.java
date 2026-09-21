package com.hardware.erp.document.service;

import com.hardware.erp.notification.dto.WhatsAppLinkResponse;
import com.hardware.erp.notification.entity.NotificationStatus;

/**
 * CR-101. Multi-channel payload assembly for a report/document that is not
 * one customer's own invoice or quotation - those already have their own
 * sharing (CR-056, CR-080; see WhatsAppLinkService/InvoiceEmailServiceImpl,
 * deliberately not duplicated here). This is the seam for the artefact type
 * that had none before this CR: a report_job's finished file.
 *
 * Direct download needs nothing from this service - it is
 * {@code GET /v1/documents/jobs/{id}/download}, already the plain file.
 */
public interface ShareDispatcherService {

    /**
     * A wa.me link with the file's caption pre-filled. Never carries the
     * file itself - a URL cannot (the same limit CR-080's own javadoc
     * documents) - so the frontend downloads the file and opens this link
     * for the owner to attach it, exactly as InvoiceDetailPage.handleShareViaApp
     * already does for an invoice PDF.
     *
     * @param toMobileNo optional; null opens WhatsApp's own contact chooser instead of a fixed chat
     */
    WhatsAppLinkResponse whatsAppLinkForJob(Long jobId, String toMobileNo);

    /** Emails the finished file as an attachment via the tenant's configured EmailTransport. */
    NotificationStatus emailJob(Long jobId, String toEmail);

    /** CR-101's first Smart Greetings entry (see WhatsAppMessageTemplates#occasionGreeting) - occasion scheduling is M8. */
    WhatsAppLinkResponse occasionGreetingLink(Long customerId, String occasionLabel);
}
