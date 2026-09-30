package com.hardware.erp.notification.service.impl;

import com.hardware.erp.notification.dto.SmsDiagnosticResponse;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.NotificationSendResult;
import com.hardware.erp.notification.service.SmsDiagnosticService;
import com.hardware.erp.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * CR-085. Email and WhatsApp each had a way to prove the channel before a
 * customer depended on it; SMS did not, so a wrong Twilio credential or an
 * unregistered DLT sender stayed invisible until a customer missed a message.
 *
 * Synchronous and never throws, like MailDiagnosticServiceImpl: a broken
 * provider is the expected input here. Goes through the real provider so the
 * answer is about the path real messages take - including the SMS_ENABLED
 * switch, which is the first thing this will tell an owner is off.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsDiagnosticServiceImpl implements SmsDiagnosticService {

    private final SmsNotificationProvider smsProvider;
    private final TwilioProperties twilioProperties;

    @Override
    public SmsDiagnosticResponse sendTestSms(String toMobileNo) {
        if (!twilioProperties.enabled()) {
            return new SmsDiagnosticResponse(NotificationStatus.LOGGED_ONLY, toMobileNo, null,
                    "SMS is switched off (SMS_ENABLED=false). It is a paid channel; set SMS_ENABLED=true to turn it on.");
        }
        if (!smsProvider.isConfigured()) {
            return new SmsDiagnosticResponse(NotificationStatus.LOGGED_ONLY, toMobileNo, null,
                    "No TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN and a sender (TWILIO_MESSAGING_SERVICE_SID or "
                            + "TWILIO_FROM_NUMBER) are set, so nothing was sent.");
        }
        try {
            NotificationSendResult result = smsProvider.send(SecurityUtils.requireCurrentTenantId(),
                    NotificationChannel.SMS, toMobileNo,
                    null, "Hardware ERP test message. If you are reading this, SMS is working.");
            return new SmsDiagnosticResponse(result.status(), toMobileNo, result.providerMessageId(),
                    "Accepted by Twilio. Delivery to an Indian number also needs a DLT-registered sender; "
                            + "the status webhook updates the log when it is delivered.");
        } catch (Exception ex) {
            log.warn("Test SMS to {} failed", toMobileNo, ex);
            return new SmsDiagnosticResponse(NotificationStatus.FAILED, toMobileNo, null, rootMessage(ex));
        }
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return (message == null || message.isBlank()) ? current.getClass().getSimpleName() : message.trim();
    }
}
