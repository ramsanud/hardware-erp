package com.hardware.erp.notification.service;

/**
 * CR-074 - the one way this application sends an email, whichever backend
 * is behind it (SMTP today, Twilio SendGrid when
 * {@code app.notifications.email.provider=sendgrid}).
 *
 * Exactly one implementation is ever constructed - each is
 * {@code @ConditionalOnProperty} on that key, the same pattern the AI
 * ChatCompletionClient beans already use - so this can be injected by type.
 *
 * <p>It exists because CR-074 would otherwise have left the application with
 * two divergent email paths: the notification channel on SendGrid, and
 * password-reset mail plus the Settings "test email" button still talking
 * SMTP directly to JavaMailSender. A deployment configured with a SendGrid
 * key and no MAIL_USER would have looked fine, sent invoices, and silently
 * dropped every password-reset link - a failure nobody sees until a locked-out
 * owner reports it.
 *
 * <p>Separate from {@link NotificationProvider} rather than replacing it:
 * NotificationProvider is the channel-agnostic contract
 * NotificationServiceImpl discovers by channel, and callers like password
 * reset have no channel to choose - they are always email, and asking them
 * to pass {@code NotificationChannel.EMAIL} to prove it would be noise.
 */
public interface EmailTransport {

    /** False when this deployment has no real mail account behind it - callers log instead of sending. */
    boolean isConfigured();

    /** The address mail is sent from, or null when unconfigured. Shown by the mail diagnostic, never used to route. */
    String senderAddress();

    /**
     * What an owner should actually set to fix an unconfigured deployment.
     * Provider-specific on purpose: telling someone running SendGrid to
     * "set MAIL_USER" sends them to the wrong place entirely.
     */
    String unconfiguredHint();

    /**
     * Sends one email. {@code attachment} may be null.
     *
     * Same contract as {@link NotificationProvider#send}: returns SENT or
     * LOGGED_ONLY, and throws when a configured provider genuinely fails, so
     * that the caller stays the single place deciding what a failure means -
     * a FAILED log row for a notification, a swallowed warning for a
     * password reset, the mail server's own rejection text for the diagnostic.
     */
    NotificationSendResult sendEmail(String toAddress, String subject, String body, NotificationAttachment attachment);
}
