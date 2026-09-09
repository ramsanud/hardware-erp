package com.hardware.erp.notification;

import com.hardware.erp.notification.dto.MailDiagnosticResponse;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.NotificationAttachment;
import com.hardware.erp.notification.service.NotificationSendResult;
import com.hardware.erp.notification.service.impl.EmailNotificationProvider;
import com.hardware.erp.notification.service.impl.MailDiagnosticServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CR-074 - sends a real email through a real mail server, using the whole
 * chain the application uses: MailDiagnosticServiceImpl -> EmailTransport ->
 * EmailNotificationProvider -> JavaMailSender -> SMTP.
 *
 * <p><b>Opt-in, and deliberately so.</b> It is skipped unless
 * {@code MAIL_LIVE_TEST=true} is set in the environment. Everything else in
 * this suite either mocks the transport or asserts the built message, because
 * a test that sends mail on every {@code mvn verify} would spam a real
 * mailbox, depend on the network, and fail for reasons that have nothing to
 * do with the code. This one exists for the question those cannot answer:
 * <i>do the credentials in this environment actually deliver?</i>
 *
 * <p>Run it with:
 * {@code MAIL_LIVE_TEST=true MAIL_HOST=... MAIL_PORT=587 MAIL_USER=... MAIL_PASSWORD=... mvn -o test -Dtest=LiveMailSmokeTest}
 *
 * <p>It sends to {@code MAIL_USER} - the configured account mails itself - so
 * running it never reaches a third party, and the result lands in a mailbox
 * whoever ran it already has open.
 */
@EnabledIfEnvironmentVariable(named = "MAIL_LIVE_TEST", matches = "true")
class LiveMailSmokeTest {

    /** A tiny but structurally valid PNG, so the attachment path carries real bytes rather than a text file renamed. */
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n',
            0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0
    };

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private EmailNotificationProvider liveProvider() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(env("MAIL_HOST", "smtp.gmail.com"));
        sender.setPort(Integer.parseInt(env("MAIL_PORT", "587")));
        sender.setUsername(env("MAIL_USER", ""));
        sender.setPassword(env("MAIL_PASSWORD", ""));
        Properties properties = sender.getJavaMailProperties();
        // The same two settings application.yml sets for the running app -
        // Gmail refuses the login without STARTTLS.
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.timeout", "20000");
        properties.put("mail.smtp.connectiontimeout", "20000");

        EmailNotificationProvider provider = new EmailNotificationProvider(sender);
        ReflectionTestUtils.setField(provider, "fromAddress", env("MAIL_USER", ""));
        return provider;
    }

    @Test
    @DisplayName("the Settings test-email path delivers a real message through the real mail server")
    void theDiagnosticPathDeliversForReal() {
        MailDiagnosticServiceImpl diagnostic = new MailDiagnosticServiceImpl(liveProvider());

        MailDiagnosticResponse response = diagnostic.sendTestEmail(env("MAIL_USER", ""));

        // FAILED carries the mail server's own rejection text, which is the
        // whole reason the diagnostic reports it rather than a boolean.
        assertThat(response.status())
                .as("mail server said: %s", response.detail())
                .isEqualTo(NotificationStatus.SENT);
        assertThat(response.fromAddress()).isEqualTo(env("MAIL_USER", ""));
    }

    @Test
    @DisplayName("an attachment survives the real SMTP round trip, not just the in-memory MIME assembly")
    void anAttachmentIsAcceptedByTheRealMailServer() {
        NotificationSendResult result = liveProvider().sendEmail(
                env("MAIL_USER", ""),
                "Hardware ERP live test - attachment",
                "This message carries a 1x1 PNG, exercising the same MIME multipart path "
                        + "used by the invoice PDF and the support screenshot.",
                new NotificationAttachment("live-test.png", "image/png", PNG));

        assertThat(result.status()).isEqualTo(NotificationStatus.SENT);
    }
}
