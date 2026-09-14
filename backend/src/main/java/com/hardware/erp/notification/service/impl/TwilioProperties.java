package com.hardware.erp.notification.service.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CR-074 - app-wide Twilio Programmable Messaging credentials, deliberately
 * NOT per tenant.
 *
 * This is the opposite choice to {@link WhatsAppProperties}/CR-056, and the
 * difference is real rather than an inconsistency: a WhatsApp Business
 * message must be sent from the shop's own verified WABA number, so the
 * token has to be the tenant's. An SMS goes out from one sender the
 * platform owns and pays for, so one account for the whole deployment is
 * both correct and the only thing that can ship without a new table, an
 * encrypted-token column and a Settings screen. A future "each shop brings
 * its own Twilio account" is a CR of its own, on the scale of CR-056 - see
 * the CR-074 entry.
 *
 * Every field blank is the supported default: {@link SmsNotificationProvider}
 * then logs instead of sending, exactly as it did before Twilio existed here.
 */
@ConfigurationProperties(prefix = "app.notifications.sms.twilio")
public record TwilioProperties(
        /**
         * CR-077 - the master switch, false by default. SMS costs money per
         * message and needs DLT registration in India, so it is opt-in even
         * when credentials are present: a deployment that inherited a
         * TWILIO_* set from somewhere must not start paying by accident.
         */
        boolean enabled,
        String apiBaseUrl,
        String accountSid,
        String authToken,
        /** The purchased Twilio number in E.164 (+15551234567). Ignored when messagingServiceSid is set. */
        String fromNumber,
        /**
         * Preferred over fromNumber when both are present: a Messaging Service
         * is what carries the DLT-registered sender id an Indian deployment
         * needs, and it lets Twilio pick from a pool rather than one number.
         */
        String messagingServiceSid
) {

    /**
     * A send needs the switch on, the account pair AND some sender. Checked
     * here rather than in the provider so "can SMS send" has exactly one
     * definition - every caller that already asked isConfigured() got the
     * CR-077 switch for free.
     */
    public boolean isConfigured() {
        return enabled && notBlank(accountSid) && notBlank(authToken)
                && (notBlank(messagingServiceSid) || notBlank(fromNumber));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
