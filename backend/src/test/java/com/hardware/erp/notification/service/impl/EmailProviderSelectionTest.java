package com.hardware.erp.notification.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.service.EmailTransport;
import com.hardware.erp.notification.service.NotificationProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CR-074 - proves the SMTP/SendGrid switch actually resolves, in a real
 * application context.
 *
 * This is the layer the provider unit tests cannot reach: they construct each
 * provider directly, so they would keep passing even if both beans were
 * active at once, or neither was. Two beans claiming
 * {@link NotificationChannel#EMAIL} collide silently in
 * NotificationServiceImpl's channel map (last one wins, no error), and zero
 * beans means nothing that injects {@link EmailTransport} - password reset,
 * invoice mail, the Settings diagnostic - can start at all.
 *
 * Uses {@link ApplicationContextRunner} rather than {@code @SpringBootTest} so
 * it runs in milliseconds without Docker or a database: the question is only
 * which of two beans the condition produces.
 */
class EmailProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(EmailProvidersUnderTest.class)
            // The SMTP provider needs a JavaMailSender; a mock is enough, since
            // nothing here sends anything.
            .withBean(org.springframework.mail.javamail.JavaMailSender.class,
                    () -> org.mockito.Mockito.mock(org.springframework.mail.javamail.JavaMailSender.class));

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ SendGridProperties.class, TwilioProperties.class })
    @Import({ EmailNotificationProvider.class, SendGridEmailProvider.class })
    static class EmailProvidersUnderTest {
    }

    @Test
    @DisplayName("by default the SMTP provider is the one and only EmailTransport")
    void smtpIsTheDefault() {
        contextRunner.run(context -> {
            // matchIfMissing = true: a deployment that sets nothing keeps the
            // behaviour it had before CR-074 existed.
            assertThat(context).hasSingleBean(EmailTransport.class);
            assertThat(context).getBean(EmailTransport.class).isInstanceOf(EmailNotificationProvider.class);
            assertThat(context).doesNotHaveBean(SendGridEmailProvider.class);
        });
    }

    @Test
    @DisplayName("an explicit smtp setting selects the same provider as the default")
    void explicitSmtpSelectsTheSmtpProvider() {
        contextRunner.withPropertyValues("app.notifications.email.provider=smtp").run(context -> {
            assertThat(context).hasSingleBean(EmailTransport.class);
            assertThat(context).getBean(EmailTransport.class).isInstanceOf(EmailNotificationProvider.class);
        });
    }

    @Test
    @DisplayName("provider=sendgrid swaps the transport, and the SMTP bean is gone entirely")
    void sendGridReplacesSmtpRatherThanJoiningIt() {
        contextRunner.withPropertyValues(
                "app.notifications.email.provider=sendgrid",
                "app.notifications.email.sendgrid.api-base-url=https://api.sendgrid.com/v3",
                "app.notifications.email.sendgrid.api-key=SG.test",
                "app.notifications.email.sendgrid.from-email=billing@sarahardware.in").run(context -> {
            assertThat(context).hasSingleBean(EmailTransport.class);
            assertThat(context).getBean(EmailTransport.class).isInstanceOf(SendGridEmailProvider.class);
            // The point of the switch: not both. Two beans claiming EMAIL would
            // resolve by iteration order, and the loser would fail silently.
            assertThat(context).doesNotHaveBean(EmailNotificationProvider.class);
        });
    }

    @Test
    @DisplayName("whichever provider is active, exactly one bean claims the EMAIL channel")
    void exactlyOneProviderClaimsTheEmailChannel() {
        assertOneEmailChannelProvider();
        assertOneEmailChannelProvider("app.notifications.email.provider=sendgrid",
                "app.notifications.email.sendgrid.api-key=SG.test",
                "app.notifications.email.sendgrid.from-email=billing@sarahardware.in");
    }

    private void assertOneEmailChannelProvider(String... properties) {
        contextRunner.withPropertyValues(properties).run(context -> {
            long emailProviders = context.getBeansOfType(NotificationProvider.class).values().stream()
                    .filter(provider -> provider.supportedChannels().contains(NotificationChannel.EMAIL))
                    .count();
            assertThat(emailProviders)
                    .as("exactly one NotificationProvider must claim EMAIL")
                    .isEqualTo(1);
        });
    }

    /**
     * The SendGrid bean binds its own properties, and a typo in the prefix
     * would leave every field null - which reads as "unconfigured" and
     * silently logs instead of sending, the hardest failure of all to notice.
     */
    @Test
    @DisplayName("SendGrid's configuration properties bind from the app.notifications.email.sendgrid prefix")
    void sendGridPropertiesBindFromTheExpectedPrefix() {
        contextRunner.withPropertyValues(
                "app.notifications.email.provider=sendgrid",
                "app.notifications.email.sendgrid.api-base-url=https://api.sendgrid.com/v3",
                "app.notifications.email.sendgrid.api-key=SG.bound",
                "app.notifications.email.sendgrid.from-email=billing@sarahardware.in",
                "app.notifications.email.sendgrid.from-name=Sara Hardware").run(context -> {
            SendGridProperties properties = context.getBean(SendGridProperties.class);
            assertThat(properties.apiKey()).isEqualTo("SG.bound");
            assertThat(properties.fromEmail()).isEqualTo("billing@sarahardware.in");
            assertThat(properties.fromName()).isEqualTo("Sara Hardware");
            assertThat(properties.isConfigured()).isTrue();
            assertThat(context.getBean(EmailTransport.class).senderAddress())
                    .isEqualTo("billing@sarahardware.in");
        });
    }

    /** Same check for Twilio - one prefix typo is the whole difference between sending and logging. */
    @Test
    @DisplayName("Twilio's configuration properties bind from the app.notifications.sms.twilio prefix")
    void twilioPropertiesBindFromTheExpectedPrefix() {
        contextRunner.withPropertyValues(
                "app.notifications.sms.twilio.api-base-url=https://api.twilio.com/2010-04-01",
                "app.notifications.sms.twilio.account-sid=ACtest",
                "app.notifications.sms.twilio.auth-token=token",
                "app.notifications.sms.twilio.messaging-service-sid=MGtest").run(context -> {
            TwilioProperties properties = context.getBean(TwilioProperties.class);
            assertThat(properties.accountSid()).isEqualTo("ACtest");
            assertThat(properties.messagingServiceSid()).isEqualTo("MGtest");
            assertThat(properties.isConfigured()).isTrue();
            // Constructed the way Spring constructs it - the two-arg constructor
            // is the one @Autowired marks.
            assertThat(new SmsNotificationProvider(properties, new ObjectMapper()).isConfigured()).isTrue();
        });
    }

    @Test
    @DisplayName("with nothing configured both channels report unconfigured rather than half-configured")
    void nothingConfiguredMeansUnconfigured() {
        contextRunner.run(context -> {
            assertThat(context.getBean(EmailTransport.class).isConfigured()).isFalse();
            assertThat(context.getBean(TwilioProperties.class).isConfigured()).isFalse();
            // The hint has to name what to set, or an owner has nothing to act on.
            assertThat(context.getBean(EmailTransport.class).unconfiguredHint()).contains("MAIL_USER");
        });
    }
}
