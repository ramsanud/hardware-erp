package com.hardware.erp.invoice.service.impl;

import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.service.InvoiceEmailService;
import com.hardware.erp.invoice.service.InvoiceService;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.EmailTransport;
import com.hardware.erp.notification.service.NotificationAttachment;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Deliberately synchronous (unlike NotificationService's @Async triggers) -
 * the owner clicked "Email" and is waiting to see whether it actually sent,
 * the same reasoning NotificationService.contactAdmin() already documents
 * for its own synchronous send.
 *
 * Sends through {@link EmailTransport} since CR-074 rather than reaching for
 * JavaMailSender itself, so that switching this deployment's email provider
 * to SendGrid moves invoice mail with it instead of leaving this one path
 * quietly bound to an SMTP account that may no longer be configured.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceEmailServiceImpl implements InvoiceEmailService {

    private final InvoiceService invoiceService;
    private final TenantRepository tenantRepository;
    private final EmailTransport emailTransport;

    @Override
    public NotificationStatus emailInvoicePdf(Long invoiceId, String toEmail) {
        InvoiceResponse invoice = invoiceService.get(invoiceId);
        byte[] pdf = invoiceService.generatePdf(invoiceId);
        // findById (not getReferenceById) - this method has no surrounding
        // @Transactional of its own, and getReferenceById's lazy proxy needs
        // an active Hibernate session at the point .getName() is called,
        // which no longer exists once invoiceService.get()'s own transaction
        // has closed. findById resolves eagerly within its own repository-
        // level transaction, so it never depends on one still being open here.
        String shopName = tenantRepository.findById(SecurityUtils.requireCurrentTenantId())
                .map(com.hardware.erp.tenant.entity.Tenant::getName)
                .orElse("");

        try {
            // LOGGED_ONLY when no mail account is configured comes back from the
            // transport itself now, rather than being decided here - one
            // definition of "unconfigured", whichever provider is active.
            return emailTransport.sendEmail(toEmail,
                    "Tax Invoice " + invoice.invoiceNumber() + " from " + shopName,
                    """
                    Hello %s,

                    Please find attached your tax invoice %s for %s, dated %s.

                    Thank you for your business.
                    %s
                    """.formatted(invoice.customerName(), invoice.invoiceNumber(),
                            invoice.totalDisplay(), invoice.invoiceDate(), shopName),
                    new NotificationAttachment(invoice.invoiceNumber() + ".pdf", "application/pdf", pdf))
                    .status();
        } catch (Exception e) {
            log.error("Failed to email invoice {}", invoice.invoiceNumber(), e);
            return NotificationStatus.FAILED;
        }
    }
}
