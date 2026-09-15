package com.hardware.erp.notification.dto;

import com.hardware.erp.notification.entity.NotificationStatus;

/**
 * CR-085. The honest result of trying to send one test SMS - the same shape
 * as MailDiagnosticResponse, for the same reason: Twilio's own error text
 * ("21608 - unverified trial number", "21614 - not a mobile number") is what
 * tells the owner what to fix.
 */
public record SmsDiagnosticResponse(
        NotificationStatus status,
        String toMobileNo,
        String providerMessageId,
        String detail
) {}
