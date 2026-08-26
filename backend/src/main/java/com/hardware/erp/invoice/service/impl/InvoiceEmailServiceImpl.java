package com.hardware.erp.invoice.service.impl;

import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.service.InvoiceEmailService;
import com.hardware.erp.invoice.service.InvoiceService;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Deliberately synchronous (unlike NotificationService's @Async triggers) -
 * the owner clicked "Email" and is waiting to see whether it actually sent,
 * the same reasoning NotificationService.contactAdmin() already documents
 * for its own synchronous send.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceEmailServiceImpl implements InvoiceEmailService {

    private final InvoiceService invoiceService;
    private final TenantRepository tenantRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

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

        if (fromAddress == null || fromAddress.isBlank()) {
            log.info("Mail not configured - would have emailed invoice {} to {}", invoice.invoiceNumber(), toEmail);
            return NotificationStatus.LOGGED_ONLY;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Tax Invoice " + invoice.invoiceNumber() + " from " + shopName);
            helper.setText("""
                    Hello %s,

                    Please find attached your tax invoice %s for %s, dated %s.

                    Thank you for your business.
                    %s
                    """.formatted(invoice.customerName(), invoice.invoiceNumber(),
                    invoice.totalDisplay(), invoice.invoiceDate(), shopName));
            helper.addAttachment(invoice.invoiceNumber() + ".pdf",
                    new org.springframework.core.io.ByteArrayResource(pdf));
            mailSender.send(message);
            return NotificationStatus.SENT;
        } catch (Exception e) {
            log.error("Failed to email invoice {}", invoice.invoiceNumber(), e);
            return NotificationStatus.FAILED;
        }
    }
}
