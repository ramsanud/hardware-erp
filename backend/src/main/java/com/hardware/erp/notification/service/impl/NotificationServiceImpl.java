package com.hardware.erp.notification.service.impl;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import com.hardware.erp.invoice.entity.Payment;
import com.hardware.erp.notification.dto.NotificationLogResponse;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationLog;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.repository.NotificationLogRepository;
import com.hardware.erp.notification.service.NotificationProvider;
import com.hardware.erp.notification.service.NotificationSendResult;
import com.hardware.erp.notification.service.NotificationService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Channel choice (deliberate, not a placeholder): SMS is sent whenever the
 * customer has a mobile number, which - per the customer table's own NOT
 * NULL constraint - is effectively always. Email is sent additionally
 * whenever an email is also on file. This is "send to every address we
 * have", not "prefer one over the other": a hardware shop's walk-in
 * customer overwhelmingly gives a mobile number and rarely an email, so
 * treating email as the primary channel would leave most customers
 * unnotified, but a customer who *did* bother to give an email typically
 * checks it, so there is no reason to suppress that channel just because
 * SMS also fired.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final String ENTITY_INVOICE = "INVOICE";
    /** Distinct from ENTITY_INVOICE so the once-a-day dedup in notifyPaymentDue never confuses a reminder with the invoice-created notification. */
    private static final String ENTITY_INVOICE_REMINDER = "INVOICE_REMINDER";
    private static final String RUPEE = "₹";

    private final List<NotificationProvider> providers;
    private final NotificationLogRepository notificationLogRepository;
    private final TenantRepository tenantRepository;

    @Value("${app.support.admin-email:}")
    private String adminEmail;

    private Map<NotificationChannel, NotificationProvider> providersByChannel;

    @PostConstruct
    void indexProviders() {
        providersByChannel = new EnumMap<>(NotificationChannel.class);
        for (NotificationProvider provider : providers) {
            for (NotificationChannel channel : provider.supportedChannels()) {
                providersByChannel.put(channel, provider);
            }
        }
    }

    @Override
    @Async("taskExecutor")
    public void notifyInvoiceCreated(Invoice invoice) {
        String body = "Your invoice %s for %s%s has been generated.".formatted(
                invoice.getInvoiceNumber(), RUPEE, IndianCurrencyFormat.rupees(invoice.getTotalPaise()));
        String subject = "Invoice " + invoice.getInvoiceNumber() + " generated";
        sendToCustomer(invoice.getTenant().getId(), invoice.getCustomer(), subject, body,
                ENTITY_INVOICE, invoice.getId());
    }

    @Override
    @Async("taskExecutor")
    public void notifyPaymentReceived(Invoice invoice, Payment payment) {
        String body = "We received your payment of %s%s towards invoice %s. Balance: %s%s.".formatted(
                RUPEE, IndianCurrencyFormat.rupees(payment.getAmountPaise()), invoice.getInvoiceNumber(),
                RUPEE, IndianCurrencyFormat.rupees(invoice.getBalancePaise()));
        String subject = "Payment received for " + invoice.getInvoiceNumber();
        sendToCustomer(invoice.getTenant().getId(), invoice.getCustomer(), subject, body,
                ENTITY_INVOICE, invoice.getId());
    }

    @Override
    public NotificationStatus contactAdmin(String subject, String message) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        AppUserDetails caller = SecurityUtils.requireCurrentUser();
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        String shopName = tenant != null ? tenant.getName() : ("tenant " + tenantId);

        String body = "Shop: %s\nReported by: %s (%s)\n\n%s".formatted(
                shopName, caller.getFullName(), caller.getMobileNo(), message);
        String fullSubject = "[Support] " + shopName + " - " + subject;

        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("app.support.admin-email is not configured - support request logged only, not emailed");
            notificationLogRepository.save(NotificationLog.builder()
                    .tenantId(tenantId).channel(NotificationChannel.EMAIL)
                    .recipient("(admin email not configured)")
                    .subject(fullSubject).body(body).status(NotificationStatus.LOGGED_ONLY)
                    .relatedEntityType("SUPPORT").relatedEntityId(caller.getId())
                    .build());
            return NotificationStatus.LOGGED_ONLY;
        }

        NotificationProvider provider = providersByChannel.get(NotificationChannel.EMAIL);
        NotificationStatus status;
        try {
            status = provider.send(tenantId, NotificationChannel.EMAIL, adminEmail, fullSubject, body).status();
        } catch (Exception ex) {
            log.error("Failed to email support request to admin", ex);
            status = NotificationStatus.FAILED;
        }

        notificationLogRepository.save(NotificationLog.builder()
                .tenantId(tenantId).channel(NotificationChannel.EMAIL).recipient(adminEmail)
                .subject(fullSubject).body(body).status(status)
                .relatedEntityType("SUPPORT").relatedEntityId(caller.getId())
                .build());

        if (status == NotificationStatus.FAILED) {
            throw new BusinessException("Could not send your message right now. Please try again shortly.");
        }
        return status;
    }

    @Override
    public PageResponse<NotificationLogResponse> search(NotificationChannel channel, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        var page = channel == null
                ? notificationLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                : notificationLogRepository.findByTenantIdAndChannelOrderByCreatedAtDesc(tenantId, channel, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    /**
     * Task 05 (WhatsApp reminders, MUST-HAVE). Synchronous, unlike the
     * @Async trigger methods above - a person clicking "Send WhatsApp
     * reminder" on the Invoice Detail page needs to see the real resulting
     * status (Sent / Logged only / Failed), not have it happen invisibly in
     * the background. Deduplicated to once per calendar day per invoice so
     * a shop cannot accidentally spam a customer by clicking the button
     * repeatedly - matches the once-daily cadence of {@code ReminderSchedulerService}'s
     * own shop-facing digest.
     */
    @Override
    @Transactional
    public NotificationStatus notifyPaymentDue(Invoice invoice) {
        Long tenantId = invoice.getTenant().getId();

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("This invoice is cancelled - there is nothing to remind the customer about.");
        }
        if (invoice.getBalancePaise() <= 0) {
            throw new BusinessException("This invoice has no outstanding balance.");
        }
        Customer customer = invoice.getCustomer();
        requireWhatsAppEligible(customer);

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        boolean alreadyRemindedToday = notificationLogRepository
                .existsByTenantIdAndChannelAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                        tenantId, NotificationChannel.WHATSAPP, ENTITY_INVOICE_REMINDER, invoice.getId(), startOfToday);
        if (alreadyRemindedToday) {
            throw new BusinessException("A payment reminder was already sent for this invoice today.");
        }

        String body = "Reminder: %s%s is still due on invoice %s. Please pay at your earliest convenience. Thank you!"
                .formatted(RUPEE, IndianCurrencyFormat.rupees(invoice.getBalancePaise()), invoice.getInvoiceNumber());

        return attempt(tenantId, NotificationChannel.WHATSAPP, customer.getMobileNo(), null, body,
                ENTITY_INVOICE_REMINDER, invoice.getId());
    }

    @Override
    @Transactional
    public NotificationStatus sendInvoiceViaWhatsApp(Invoice invoice) {
        Customer customer = invoice.getCustomer();
        requireWhatsAppEligible(customer);

        String body = "Your invoice %s for %s%s has been generated.".formatted(
                invoice.getInvoiceNumber(), RUPEE, IndianCurrencyFormat.rupees(invoice.getTotalPaise()));
        return attempt(invoice.getTenant().getId(), NotificationChannel.WHATSAPP, customer.getMobileNo(), null, body,
                ENTITY_INVOICE, invoice.getId());
    }

    @Override
    @Transactional
    public NotificationStatus sendPaymentReceiptViaWhatsApp(Invoice invoice, Payment payment) {
        Customer customer = invoice.getCustomer();
        requireWhatsAppEligible(customer);

        String body = "We received your payment of %s%s towards invoice %s. Balance: %s%s.".formatted(
                RUPEE, IndianCurrencyFormat.rupees(payment.getAmountPaise()), invoice.getInvoiceNumber(),
                RUPEE, IndianCurrencyFormat.rupees(invoice.getBalancePaise()));
        return attempt(invoice.getTenant().getId(), NotificationChannel.WHATSAPP, customer.getMobileNo(), null, body,
                ENTITY_INVOICE, invoice.getId());
    }

    @Override
    @Transactional
    public NotificationStatus sendTestWhatsApp(String toMobileNo) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        NotificationProvider provider = providersByChannel.get(NotificationChannel.WHATSAPP);
        if (!(provider instanceof WhatsAppBusinessProvider whatsAppProvider) || !whatsAppProvider.isConfigured(tenantId)) {
            throw new BusinessException("WhatsApp is not connected. Connect your WhatsApp Business account to send a test message.");
        }
        String body = "This is a test message from your Hardware ERP WhatsApp connection. "
                + "If you received this, sending is working correctly.";
        return attempt(tenantId, NotificationChannel.WHATSAPP, toMobileNo, null, body, "WHATSAPP_TEST", null);
    }

    /** Shared by every customer-facing WhatsApp send this class exposes - reminder, invoice, receipt. */
    private void requireWhatsAppEligible(Customer customer) {
        if (customer == null || customer.getMobileNo() == null || customer.getMobileNo().isBlank()) {
            throw new BusinessException("This customer has no mobile number on file to send a WhatsApp message to.");
        }
        if (!customer.isWhatsappOptIn()) {
            throw new BusinessException("This customer has opted out of WhatsApp messages.");
        }
    }

    // ---------------------------------------------------------------

    private void sendToCustomer(Long tenantId, Customer customer, String subject, String body,
                                 String relatedEntityType, Long relatedEntityId) {
        if (customer == null) {
            return;
        }
        boolean hasMobile = customer.getMobileNo() != null && !customer.getMobileNo().isBlank();
        boolean hasEmail = customer.getEmail() != null && !customer.getEmail().isBlank();

        if (hasMobile) {
            attempt(tenantId, NotificationChannel.SMS, customer.getMobileNo(), null, body,
                    relatedEntityType, relatedEntityId);
            // CR-056 §16 - the one place WhatsApp consent is checked for the
            // automatic (non-button) sends; SMS above is unaffected, consent
            // is WhatsApp-specific per the spec.
            if (customer.isWhatsappOptIn()) {
                attempt(tenantId, NotificationChannel.WHATSAPP, customer.getMobileNo(), null, body,
                        relatedEntityType, relatedEntityId);
            }
        }
        if (hasEmail) {
            attempt(tenantId, NotificationChannel.EMAIL, customer.getEmail(), subject, body,
                    relatedEntityType, relatedEntityId);
        }
        if (!hasMobile && !hasEmail) {
            log.info("Customer {} has neither a mobile number nor an email on file - no notification attempted",
                    customer.getId());
        }
    }

    private NotificationStatus attempt(Long tenantId, NotificationChannel channel, String toAddress, String subject,
                                        String body, String relatedEntityType, Long relatedEntityId) {
        NotificationProvider provider = providersByChannel.get(channel);
        NotificationStatus status;
        String providerMessageId = null;
        if (provider == null) {
            log.warn("No notification provider registered for channel {}", channel);
            status = NotificationStatus.FAILED;
        } else {
            try {
                NotificationSendResult result = provider.send(tenantId, channel, toAddress, subject, body);
                status = result.status();
                providerMessageId = result.providerMessageId();
            } catch (Exception ex) {
                log.error("Notification send failed on channel {} to {}", channel, toAddress, ex);
                status = NotificationStatus.FAILED;
            }
        }

        notificationLogRepository.save(NotificationLog.builder()
                .tenantId(tenantId)
                .channel(channel)
                .recipient(toAddress)
                .subject(subject)
                .body(body)
                .status(status)
                .providerMessageId(providerMessageId)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .build());
        return status;
    }

    private NotificationLogResponse toResponse(NotificationLog entry) {
        return new NotificationLogResponse(
                entry.getId(), entry.getChannel(), entry.getRecipient(), entry.getSubject(), entry.getBody(),
                entry.getStatus(), entry.getRelatedEntityType(), entry.getRelatedEntityId(),
                entry.getProviderMessageId(), entry.getCreatedAt());
    }
}
