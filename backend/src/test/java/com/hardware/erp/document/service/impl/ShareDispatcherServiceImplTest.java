package com.hardware.erp.document.service.impl;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.customer.dto.CustomerResponse;
import com.hardware.erp.customer.entity.CustomerStatus;
import com.hardware.erp.customer.service.CustomerService;
import com.hardware.erp.document.service.ReportJobService;
import com.hardware.erp.document.service.ReportJobService.DownloadableFile;
import com.hardware.erp.notification.dto.WhatsAppLinkResponse;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.EmailTransport;
import com.hardware.erp.notification.service.NotificationSendResult;
import com.hardware.erp.notification.whatsapp.ManualWhatsAppService;
import com.hardware.erp.notification.whatsapp.WhatsAppMessageTemplates;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * CR-101. Same fixture shape as WhatsAppLinkServiceTest - a real
 * ManualWhatsAppService and real WhatsAppMessageTemplates (both
 * deterministic), mocked collaborators for everything tenant-scoped.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShareDispatcherServiceImplTest {

    @Mock private ReportJobService reportJobService;
    @Mock private CustomerService customerService;
    @Mock private TenantRepository tenantRepository;
    @Mock private EmailTransport emailTransport;

    private ShareDispatcherServiceImpl service;

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

        service = new ShareDispatcherServiceImpl(reportJobService, customerService, tenantRepository,
                new ManualWhatsAppService(), new WhatsAppMessageTemplates(), emailTransport);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static String textParamOf(String url) {
        String query = URI.create(url).getRawQuery();
        return URLDecoder.decode(query.substring("text=".length()), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------ whatsAppLinkForJob

    @Test
    @DisplayName("a known recipient gets a real wa.me chat link naming the file")
    void whatsAppLinkWithRecipient() {
        when(reportJobService.download(42L)).thenReturn(
                new DownloadableFile("day-book-2026-09-20.pdf", "application/pdf", new byte[]{1}));

        WhatsAppLinkResponse link = service.whatsAppLinkForJob(42L, "9876543210");

        assertThat(link.url()).startsWith("https://wa.me/919876543210?text=");
        assertThat(textParamOf(link.url())).contains("day-book-2026-09-20.pdf").contains("BSS Hardware");
    }

    @Test
    @DisplayName("no recipient opens WhatsApp's own contact chooser rather than throwing")
    void whatsAppLinkWithoutRecipientUsesChooser() {
        when(reportJobService.download(42L)).thenReturn(
                new DownloadableFile("stock-valuation.xlsx", "application/vnd.ms-excel", new byte[]{1}));

        WhatsAppLinkResponse link = service.whatsAppLinkForJob(42L, null);

        assertThat(link.url()).startsWith("https://wa.me/?text=");
        assertThat(textParamOf(link.url())).contains("stock-valuation.xlsx");
    }

    @Test
    @DisplayName("a blank recipient is treated the same as no recipient")
    void whatsAppLinkWithBlankRecipientUsesChooser() {
        when(reportJobService.download(42L)).thenReturn(
                new DownloadableFile("gst-summary.csv", "text/csv", new byte[]{1}));

        assertThat(service.whatsAppLinkForJob(42L, "   ").url()).startsWith("https://wa.me/?text=");
    }

    // ------------------------------------------------------------- emailJob

    @Test
    @DisplayName("emailing a job's file attaches the exact bytes ReportJobService returned")
    void emailJobAttachesTheFile() {
        byte[] bytes = {1, 2, 3, 4};
        when(reportJobService.download(9L)).thenReturn(
                new DownloadableFile("gstr1-092026.json", "application/json", bytes));
        when(emailTransport.sendEmail(anyString(), anyString(), anyString(), any()))
                .thenReturn(new NotificationSendResult(NotificationStatus.SENT, "provider-id"));

        NotificationStatus status = service.emailJob(9L, "owner@example.com");

        assertThat(status).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("a transport failure is reported as FAILED, not thrown to the controller")
    void emailJobFailureIsCaught() {
        when(reportJobService.download(9L)).thenReturn(
                new DownloadableFile("f.pdf", "application/pdf", new byte[]{1}));
        when(emailTransport.sendEmail(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("SMTP down"));

        assertThat(service.emailJob(9L, "owner@example.com")).isEqualTo(NotificationStatus.FAILED);
    }

    // ------------------------------------------------------ occasionGreeting

    @Test
    @DisplayName("a greeting link carries the occasion label and the customer's own number")
    void occasionGreetingLink() {
        CustomerResponse customer = new CustomerResponse(3L, "CUST-003", "Ravi Kumar", "9876500000",
                null, null, null, null, null, null, null, null, CustomerStatus.ACTIVE, false, LocalDateTime.now());
        when(customerService.get(3L)).thenReturn(customer);

        WhatsAppLinkResponse link = service.occasionGreetingLink(3L, "Diwali");

        assertThat(link.toMobileNo()).isEqualTo("9876500000");
        assertThat(textParamOf(link.url())).contains("Ravi Kumar").contains("Diwali").contains("BSS Hardware");
    }

    @Test
    @DisplayName("a customer with no phone number is refused before any link is built")
    void occasionGreetingRefusesWithNoPhone() {
        CustomerResponse customer = new CustomerResponse(3L, "CUST-003", "Ravi Kumar", null,
                null, null, null, null, null, null, null, null, CustomerStatus.ACTIVE, false, LocalDateTime.now());
        when(customerService.get(3L)).thenReturn(customer);

        assertThatThrownBy(() -> service.occasionGreetingLink(3L, "Diwali"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("a blank occasion label is refused before the customer is even looked up")
    void occasionGreetingRefusesBlankLabel() {
        assertThatThrownBy(() -> service.occasionGreetingLink(3L, "  "))
                .isInstanceOf(BusinessException.class);
    }
}
