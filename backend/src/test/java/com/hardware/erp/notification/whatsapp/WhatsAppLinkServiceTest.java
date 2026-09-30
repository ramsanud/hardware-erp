package com.hardware.erp.notification.whatsapp;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.customer.dto.CustomerResponse;
import com.hardware.erp.customer.entity.CustomerStatus;
import com.hardware.erp.customer.service.CustomerService;
import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.dto.PaymentResponse;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import com.hardware.erp.invoice.entity.PaymentMethod;
import com.hardware.erp.invoice.service.InvoiceService;
import com.hardware.erp.notification.dto.WhatsAppLinkResponse;
import com.hardware.erp.quotation.dto.QuotationResponse;
import com.hardware.erp.quotation.entity.QuotationStatus;
import com.hardware.erp.quotation.service.QuotationService;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CR-080 - the entity-aware layer. Asserts that the right template is
 * chosen, that the customer's real number and name reach the link, and that
 * the refusals (no phone, settled invoice, foreign payment id) happen before
 * any URL is built.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppLinkServiceTest {

    @Mock private InvoiceService invoiceService;
    @Mock private QuotationService quotationService;
    @Mock private CustomerService customerService;
    @Mock private TenantRepository tenantRepository;

    private WhatsAppLinkService service;

    @BeforeEach
    void setUp() {
        Tenant tenant = Tenant.builder().id(7L).slug("bss").name("BSS Hardware")
                .status(TenantStatus.ACTIVE).build();
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));

        Role owner = Role.builder().id(1L).tenant(tenant).code("OWNER").name("Owner").build();
        User user = User.builder().id(1L).tenant(tenant).role(owner)
                .fullName("Udayakumar").mobileNo("6374005608").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(user), null, List.of()));

        // The real manual service and real templates - the thing under test is
        // how they are composed, and both are deterministic.
        service = new WhatsAppLinkService(invoiceService, quotationService, customerService,
                tenantRepository, new ManualWhatsAppService(), new WhatsAppMessageTemplates());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static String textOf(String url) {
        return URLDecoder.decode(URI.create(url).getRawQuery().substring("text=".length()), StandardCharsets.UTF_8);
    }

    private InvoiceResponse invoice(InvoiceStatus status, String mobile, List<PaymentResponse> payments) {
        return new InvoiceResponse(1024L, "INV-1024", 5L, "Ravi Kumar", mobile, LocalDate.of(2026, 8, 26),
                "10,550.00", "1,900.00", "12,450.00", null, "0.00", "0.00", "5,000.00", "7,450.00",
                status, null, null, null, null, List.of(), payments, LocalDateTime.now(), null, null, null,
                null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("an invoice link goes to the customer's number and carries name, number, total, paid and balance")
    void invoiceLink() {
        when(invoiceService.get(1024L)).thenReturn(invoice(InvoiceStatus.PARTIALLY_PAID, "98765 43210", List.of()));

        WhatsAppLinkResponse link = service.forInvoice(1024L);

        assertThat(link.url()).startsWith("https://wa.me/919876543210?text=");
        assertThat(link.toMobileNo()).isEqualTo("98765 43210");
        assertThat(textOf(link.url())).isEqualTo(link.message());
        assertThat(link.message())
                .contains("Hello Ravi Kumar")
                .contains("BSS Hardware")
                .contains("Invoice No: INV-1024")
                .contains("Total: ₹12,450.00")
                .contains("Paid: ₹5,000.00")
                .contains("Balance: ₹7,450.00");
    }

    @Test
    @DisplayName("a customer with no phone is refused with the message the UI shows")
    void invoiceWithoutPhone() {
        when(invoiceService.get(1024L)).thenReturn(invoice(InvoiceStatus.UNPAID, "", List.of()));

        assertThatThrownBy(() -> service.forInvoice(1024L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(WhatsAppLinkService.NO_PHONE);
    }

    @Test
    @DisplayName("a reminder is refused for a paid or cancelled invoice - the button must not open a wrong message")
    void reminderEligibility() {
        when(invoiceService.get(1L)).thenReturn(invoice(InvoiceStatus.PAID, "9876543210", List.of()));
        when(invoiceService.get(2L)).thenReturn(invoice(InvoiceStatus.CANCELLED, "9876543210", List.of()));
        when(invoiceService.get(3L)).thenReturn(invoice(InvoiceStatus.UNPAID, "9876543210", List.of()));

        assertThatThrownBy(() -> service.forPaymentReminder(1L)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("no outstanding balance");
        assertThatThrownBy(() -> service.forPaymentReminder(2L)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("cancelled");
        assertThat(service.forPaymentReminder(3L).message())
                .contains("friendly reminder")
                .contains("Outstanding Amount: ₹7,450.00");
    }

    @Test
    @DisplayName("a receipt is found through its own invoice - a payment id that is not on it is a 404")
    void receipt() {
        PaymentResponse payment = new PaymentResponse(88L, "5,000.00", PaymentMethod.UPI,
                LocalDateTime.of(2026, 9, 1, 10, 30), null);
        when(invoiceService.get(1024L)).thenReturn(invoice(InvoiceStatus.PARTIALLY_PAID, "9876543210", List.of(payment)));

        WhatsAppLinkResponse link = service.forPaymentReceipt(1024L, 88L);
        assertThat(link.message())
                .contains("Payment received")
                .contains("Amount Received: ₹5,000.00")
                .contains("Date: 01 Sep 2026")
                .contains("Remaining Balance: ₹7,450.00");

        assertThatThrownBy(() -> service.forPaymentReceipt(1024L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a quotation link carries the number, total and validity date")
    void quotationLink() {
        QuotationResponse quotation = new QuotationResponse(31L, "QT-0031", 5L, "Ravi Kumar", "9876543210",
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 5), false,
                "12,000.00", "0.00", "12,000.00", null, "0", "0.00", "0.00", "12,000.00", "2,160.00", "14,160.00",
                QuotationStatus.SENT, null, null, List.of(), LocalDateTime.now());
        when(quotationService.get(31L)).thenReturn(quotation);

        WhatsAppLinkResponse link = service.forQuotation(31L);
        assertThat(link.url()).startsWith("https://wa.me/919876543210?");
        assertThat(link.message())
                .contains("Quotation No: QT-0031")
                .contains("Total: ₹14,160.00")
                .contains("Valid Until: 05 Sep 2026");
    }

    @Test
    @DisplayName("a customer link is a greeting, and never touches the invoice or quotation services")
    void customerLink() {
        when(customerService.get(5L)).thenReturn(new CustomerResponse(5L, "CUS-0005", "Ravi Kumar", "9876543210",
                null, null, null, null, null, null, null, "0.00", CustomerStatus.ACTIVE, true, LocalDateTime.now()));

        WhatsAppLinkResponse link = service.forCustomer(5L);
        assertThat(link.message()).contains("Hello Ravi Kumar").contains("BSS Hardware").contains("How can we help");
        verify(invoiceService, never()).get(anyLong());
        verify(quotationService, never()).get(anyLong());
    }

    @Test
    @DisplayName("tenant scoping is the services' own - a foreign id surfaces as their 404, untouched")
    void foreignIdIsTheServicesOwn404() {
        when(invoiceService.get(999L)).thenThrow(new ResourceNotFoundException("Invoice", 999L));

        assertThatThrownBy(() -> service.forInvoice(999L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
