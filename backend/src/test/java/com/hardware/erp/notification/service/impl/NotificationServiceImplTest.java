package com.hardware.erp.notification.service.impl;

import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.entity.Payment;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationLog;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.repository.NotificationLogRepository;
import com.hardware.erp.notification.service.NotificationProvider;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceImplTest {

    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private NotificationProvider fakeProvider;
    @Mock private TenantRepository tenantRepository;

    private Tenant tenant;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).slug("default").name("Default")
                .status(TenantStatus.ACTIVE).build();

        Customer customer = Customer.builder().id(5L).tenant(tenant).customerCode("CUS-0001")
                .customerName("Ramesh Traders").mobileNo("9876500001").email("ramesh@example.com").build();

        // 1234.00 total, 734.00 still owed - matches the CR-027 spec examples exactly
        // ("...for ₹1,234.00 has been generated." / "...Balance: ₹734.00.").
        invoice = Invoice.builder().id(42L).tenant(tenant).invoiceNumber("INV-000042")
                .customer(customer).totalPaise(123400L).balancePaise(73400L).build();
    }

    /** indexProviders() is normally @PostConstruct; called by hand since this is a plain unit test, not a Spring context. */
    private NotificationServiceImpl serviceWith(NotificationProvider... providers) {
        NotificationServiceImpl service = new NotificationServiceImpl(
                List.of(providers), notificationLogRepository, tenantRepository);
        service.indexProviders();
        return service;
    }

    @Test
    @DisplayName("invoice created sends to both SMS and email when the customer has both, with the invoice number and amount in the body")
    void notifyInvoiceCreatedSendsToBothChannels() {
        when(fakeProvider.supportedChannels()).thenReturn(EnumSet.of(NotificationChannel.SMS, NotificationChannel.EMAIL));
        when(fakeProvider.send(any(), anyString(), any(), anyString())).thenReturn(NotificationStatus.SENT);

        NotificationServiceImpl service = serviceWith(fakeProvider);
        service.notifyInvoiceCreated(invoice);

        ArgumentCaptor<NotificationChannel> channelCaptor = ArgumentCaptor.forClass(NotificationChannel.class);
        ArgumentCaptor<String> toCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fakeProvider, times(2)).send(channelCaptor.capture(), toCaptor.capture(), any(), bodyCaptor.capture());

        assertThat(channelCaptor.getAllValues())
                .containsExactlyInAnyOrder(NotificationChannel.SMS, NotificationChannel.EMAIL);
        assertThat(toCaptor.getAllValues())
                .containsExactlyInAnyOrder("9876500001", "ramesh@example.com");
        assertThat(bodyCaptor.getAllValues())
                .allMatch(body -> body.contains("INV-000042") && body.contains("1,234.00"));

        verify(notificationLogRepository, times(2)).save(any(NotificationLog.class));
    }

    @Test
    @DisplayName("a customer with only a mobile number gets SMS only, not an email attempt")
    void notifyInvoiceCreatedSkipsEmailWhenCustomerHasNone() {
        Customer mobileOnly = Customer.builder().id(6L).tenant(tenant).customerCode("CUS-0002")
                .customerName("No Email Traders").mobileNo("9876500002").build();
        Invoice invoiceNoEmail = Invoice.builder().id(43L).tenant(tenant).invoiceNumber("INV-000043")
                .customer(mobileOnly).totalPaise(50000L).balancePaise(50000L).build();

        when(fakeProvider.supportedChannels()).thenReturn(EnumSet.of(NotificationChannel.SMS, NotificationChannel.EMAIL));
        when(fakeProvider.send(any(), anyString(), any(), anyString())).thenReturn(NotificationStatus.SENT);

        NotificationServiceImpl service = serviceWith(fakeProvider);
        service.notifyInvoiceCreated(invoiceNoEmail);

        verify(fakeProvider, times(1)).send(eq(NotificationChannel.SMS), eq("9876500002"), isNull(), anyString());
        verify(fakeProvider, never()).send(eq(NotificationChannel.EMAIL), anyString(), any(), anyString());
        verify(notificationLogRepository, times(1)).save(any(NotificationLog.class));
    }

    @Test
    @DisplayName("payment received includes the payment amount and the remaining balance")
    void notifyPaymentReceivedContainsAmountAndBalance() {
        when(fakeProvider.supportedChannels()).thenReturn(EnumSet.of(NotificationChannel.SMS, NotificationChannel.EMAIL));
        when(fakeProvider.send(any(), anyString(), any(), anyString())).thenReturn(NotificationStatus.SENT);

        Payment payment = Payment.builder().id(9L).tenant(tenant).invoice(invoice).amountPaise(50000L).build();

        NotificationServiceImpl service = serviceWith(fakeProvider);
        service.notifyPaymentReceived(invoice, payment);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fakeProvider, times(2)).send(any(), anyString(), any(), bodyCaptor.capture());
        assertThat(bodyCaptor.getAllValues()).allMatch(body ->
                body.contains("INV-000042") && body.contains("500.00") && body.contains("734.00"));
    }

    @Test
    @DisplayName("a provider that throws still gets a FAILED log entry, and the exception never escapes")
    void providerFailureIsRecordedNotPropagated() {
        when(fakeProvider.supportedChannels()).thenReturn(EnumSet.of(NotificationChannel.SMS, NotificationChannel.EMAIL));
        when(fakeProvider.send(any(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("gateway down"));

        NotificationServiceImpl service = serviceWith(fakeProvider);

        assertThatCode(() -> service.notifyInvoiceCreated(invoice)).doesNotThrowAnyException();

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(logCaptor.capture());
        assertThat(logCaptor.getAllValues()).allMatch(entry -> entry.getStatus() == NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("a channel with no registered provider is recorded as FAILED instead of throwing")
    void missingProviderForChannelIsRecordedAsFailed() {
        // fakeProvider only claims EMAIL, so the SMS attempt has nowhere to go.
        when(fakeProvider.supportedChannels()).thenReturn(EnumSet.of(NotificationChannel.EMAIL));
        when(fakeProvider.send(any(), anyString(), any(), anyString())).thenReturn(NotificationStatus.SENT);

        NotificationServiceImpl service = serviceWith(fakeProvider);
        service.notifyInvoiceCreated(invoice);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(logCaptor.capture());
        NotificationLog smsEntry = logCaptor.getAllValues().stream()
                .filter(entry -> entry.getChannel() == NotificationChannel.SMS)
                .findFirst().orElseThrow();
        assertThat(smsEntry.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    // ---------------------------------------------------------------
    // Real providers, not mocks - proving the "unconfigured" path in each
    // stub logs instead of throwing, exactly like SmtpMailService already
    // does for password-reset mail.

    @Test
    @DisplayName("an unconfigured email provider logs instead of sending, and never throws")
    void unconfiguredEmailProviderLogsInsteadOfSending() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        // fromAddress is left unset (null) here exactly as it would be when
        // spring.mail.username is blank in a real container - this is the
        // "SMTP not configured" state.
        EmailNotificationProvider emailProvider = new EmailNotificationProvider(mailSender);

        NotificationStatus status = emailProvider.send(NotificationChannel.EMAIL, "ramesh@example.com",
                "Invoice INV-000042 generated", "Your invoice INV-000042 for ₹1,234.00 has been generated.");

        assertThat(status).isEqualTo(NotificationStatus.LOGGED_ONLY);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("the SMS/WhatsApp stub always logs and never throws, for either channel")
    void smsWhatsAppStubAlwaysLogsOnly() {
        SmsWhatsAppNotificationProvider provider = new SmsWhatsAppNotificationProvider();

        assertThat(provider.supportedChannels())
                .containsExactlyInAnyOrder(NotificationChannel.SMS, NotificationChannel.WHATSAPP);
        assertThat(provider.send(NotificationChannel.SMS, "9876500001", null, "test message"))
                .isEqualTo(NotificationStatus.LOGGED_ONLY);
        assertThat(provider.send(NotificationChannel.WHATSAPP, "9876500001", null, "test message"))
                .isEqualTo(NotificationStatus.LOGGED_ONLY);
    }
}
