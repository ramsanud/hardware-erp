package com.hardware.erp.notification.whatsapp;

import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.customer.dto.CustomerResponse;
import com.hardware.erp.customer.service.CustomerService;
import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.dto.PaymentResponse;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import com.hardware.erp.invoice.service.InvoiceService;
import com.hardware.erp.notification.dto.WhatsAppLinkResponse;
import com.hardware.erp.quotation.dto.QuotationResponse;
import com.hardware.erp.quotation.service.QuotationService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * CR-080 - "give me the WhatsApp link for this invoice / quotation / payment /
 * customer". The entity-aware layer: it knows which customer a document
 * belongs to and which template fits, and hands the rest to
 * {@link WhatsAppService}.
 *
 * <p><b>Tenant isolation is inherited, not re-implemented.</b> Every lookup
 * goes through {@code InvoiceService.get}, {@code QuotationService.get} or
 * {@code CustomerService.get}, each of which already resolves the caller's
 * tenant from the JWT and answers 404 for anyone else's id. There is no
 * repository access in this class at all, so there is no new place for a
 * cross-tenant read to be written.
 *
 * <p>Nothing here is logged: a returned link is the customer's number and the
 * message text, and the log is not where either belongs.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppLinkService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    static final String NO_PHONE = "This customer does not have a phone number.";

    private final InvoiceService invoiceService;
    private final QuotationService quotationService;
    private final CustomerService customerService;
    private final TenantRepository tenantRepository;
    private final WhatsAppService whatsAppService;
    private final WhatsAppMessageTemplates templates;

    @Transactional(readOnly = true)
    public WhatsAppLinkResponse forInvoice(Long invoiceId) {
        InvoiceResponse invoice = invoiceService.get(invoiceId);
        String message = templates.invoice(shopName(), invoice.customerName(), invoice.invoiceNumber(),
                invoice.totalDisplay(), invoice.paidDisplay(), invoice.balanceDisplay());
        return link(invoice.customerMobile(), message);
    }

    /**
     * Same eligibility as the automatic reminder (NotificationServiceImpl
     * .notifyPaymentDue): a cancelled or settled invoice has nothing to
     * remind anyone about, and the button should say so rather than open a
     * chat with a wrong message.
     */
    @Transactional(readOnly = true)
    public WhatsAppLinkResponse forPaymentReminder(Long invoiceId) {
        InvoiceResponse invoice = invoiceService.get(invoiceId);
        if (invoice.status() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("This invoice is cancelled - there is nothing to remind the customer about.");
        }
        if (invoice.status() != InvoiceStatus.UNPAID && invoice.status() != InvoiceStatus.PARTIALLY_PAID) {
            throw new BusinessException("This invoice has no outstanding balance.");
        }
        String message = templates.paymentReminder(shopName(), invoice.customerName(),
                invoice.invoiceNumber(), invoice.balanceDisplay());
        return link(invoice.customerMobile(), message);
    }

    @Transactional(readOnly = true)
    public WhatsAppLinkResponse forPaymentReceipt(Long invoiceId, Long paymentId) {
        InvoiceResponse invoice = invoiceService.get(invoiceId);
        // The payment is reached through its invoice, never looked up on its
        // own - so a payment id from another tenant's invoice is unreachable
        // by construction, not by a check that could be forgotten.
        PaymentResponse payment = invoice.payments().stream()
                .filter(candidate -> candidate.id().equals(paymentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        String message = templates.paymentReceipt(shopName(), invoice.customerName(), invoice.invoiceNumber(),
                payment.amountDisplay(), date(payment.paymentDate()), invoice.balanceDisplay());
        return link(invoice.customerMobile(), message);
    }

    @Transactional(readOnly = true)
    public WhatsAppLinkResponse forQuotation(Long quotationId) {
        QuotationResponse quotation = quotationService.get(quotationId);
        String message = templates.quotation(shopName(), quotation.customerName(), quotation.quotationNumber(),
                quotation.totalDisplay(), date(quotation.validUntil()));
        return link(quotation.customerMobile(), message);
    }

    @Transactional(readOnly = true)
    public WhatsAppLinkResponse forCustomer(Long customerId) {
        CustomerResponse customer = customerService.get(customerId);
        return link(customer.mobileNo(), templates.customerGreeting(shopName(), customer.customerName()));
    }

    // ---------------------------------------------------------------

    private WhatsAppLinkResponse link(String mobileNo, String message) {
        if (mobileNo == null || mobileNo.isBlank()) {
            throw new BusinessException(NO_PHONE);
        }
        // Normalisation happens inside generateChatUrl and refuses anything it
        // cannot vouch for; the display value stays as the shop entered it.
        return new WhatsAppLinkResponse(whatsAppService.generateChatUrl(mobileNo, message), mobileNo, message);
    }

    private String shopName() {
        return tenantRepository.findById(SecurityUtils.requireCurrentTenantId())
                .map(Tenant::getName)
                .orElse("");
    }

    private static String date(LocalDate value) {
        return value == null ? "-" : DATE.format(value);
    }

    private static String date(LocalDateTime value) {
        return value == null ? "-" : DATE.format(value);
    }
}
