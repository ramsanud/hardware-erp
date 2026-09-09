package com.hardware.erp.notification;

import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.NotificationAttachment;
import com.hardware.erp.notification.service.NotificationProvider;
import com.hardware.erp.notification.service.NotificationSendResult;
import com.hardware.erp.notification.service.impl.EmailNotificationProvider;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CR-073 - the support screenshot reaches the admin mailbox.
 *
 * These assert the SHAPE of what is handed to JavaMailSender rather than
 * mocking the send and trusting it: the whole point of the change is that a
 * message carrying a file must become a MIME multipart, and only reading the
 * built message proves that happened.
 */
class EmailAttachmentTest {

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4 };

    /** Captures whatever the provider builds, and creates real MimeMessages so the multipart is genuinely assembled. */
    private static class CapturingMailSender implements JavaMailSender {
        final AtomicReference<SimpleMailMessage> simple = new AtomicReference<>();
        final AtomicReference<MimeMessage> mime = new AtomicReference<>();

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(java.io.InputStream contentStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            mime.set(mimeMessages[0]);
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            simple.set(simpleMessages[0]);
        }
    }

    private EmailNotificationProvider providerWithSmtp(CapturingMailSender sender) {
        EmailNotificationProvider provider = new EmailNotificationProvider(sender);
        // Blank means "no SMTP behind this deployment", which short-circuits to
        // LOGGED_ONLY - so a configured address is what puts the send path under test.
        ReflectionTestUtils.setField(provider, "fromAddress", "support@sarahardware.in");
        return provider;
    }

    @Test
    void aMessageWithNoAttachmentStaysOnThePlainTextPath() {
        CapturingMailSender sender = new CapturingMailSender();

        NotificationSendResult result = providerWithSmtp(sender).send(
                1L, NotificationChannel.EMAIL, "admin@erp.in", "[Support] subject", "body", null);

        assertThat(result.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(sender.simple.get()).isNotNull();
        assertThat(sender.simple.get().getText()).isEqualTo("body");
        // Not a MIME multipart: a plain mail must not gain one just because
        // the method is now capable of building one.
        assertThat(sender.mime.get()).isNull();
    }

    @Test
    void anAttachedScreenshotIsSentAsAMimeMultipartPart() throws Exception {
        CapturingMailSender sender = new CapturingMailSender();
        NotificationAttachment shot = new NotificationAttachment("bug.png", "image/png", PNG);

        NotificationSendResult result = providerWithSmtp(sender).send(
                1L, NotificationChannel.EMAIL, "admin@erp.in", "[Support] subject", "body", shot);

        assertThat(result.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(sender.simple.get()).isNull();

        MimeMessage sent = sender.mime.get();
        assertThat(sent).isNotNull();
        assertThat(sent.getSubject()).isEqualTo("[Support] subject");

        MimeMultipart multipart = (MimeMultipart) sent.getContent();
        boolean carriesTheFile = false;
        for (int i = 0; i < multipart.getCount(); i++) {
            String name = multipart.getBodyPart(i).getFileName();
            if ("bug.png".equals(name)) carriesTheFile = true;
        }
        assertThat(carriesTheFile)
                .as("the built message should carry bug.png as an attachment part")
                .isTrue();
    }

    @Test
    void anUnconfiguredMailboxStillLogsRatherThanFailingWhenAFileIsAttached() {
        CapturingMailSender sender = new CapturingMailSender();
        EmailNotificationProvider provider = new EmailNotificationProvider(sender);
        ReflectionTestUtils.setField(provider, "fromAddress", "");

        NotificationSendResult result = provider.send(1L, NotificationChannel.EMAIL, "admin@erp.in",
                "s", "b", new NotificationAttachment("bug.png", "image/png", PNG));

        assertThat(result.status()).isEqualTo(NotificationStatus.LOGGED_ONLY);
        assertThat(sender.mime.get()).isNull();
    }

    /**
     * The default on the interface is the reason SMS and WhatsApp needed no
     * change at all. A channel that cannot carry a file must still deliver
     * the message rather than reject it.
     */
    @Test
    void aChannelThatCannotCarryFilesIgnoresTheAttachmentAndStillSends() {
        AtomicReference<String> delivered = new AtomicReference<>();
        NotificationProvider smsLike = new NotificationProvider() {
            @Override
            public Set<NotificationChannel> supportedChannels() {
                return EnumSet.of(NotificationChannel.SMS);
            }

            @Override
            public NotificationSendResult send(Long tenantId, NotificationChannel channel,
                                               String toAddress, String subject, String body) {
                delivered.set(body);
                return NotificationSendResult.sent("id-1");
            }
        };

        NotificationSendResult result = smsLike.send(1L, NotificationChannel.SMS, "9876543210",
                null, "your invoice is due", new NotificationAttachment("bug.png", "image/png", PNG));

        assertThat(result.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(delivered.get()).isEqualTo("your invoice is due");
    }

    @Test
    void describeReportsTheFileWithoutExposingItsBytes() {
        NotificationAttachment shot = new NotificationAttachment("bug.png", "image/png", new byte[3000]);
        assertThat(shot.describe()).isEqualTo("bug.png (image/png, 2 KB)");
    }
}
