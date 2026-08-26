package com.hardware.erp.notification.service;

import com.hardware.erp.notification.dto.MailDiagnosticResponse;

public interface MailDiagnosticService {

    /** Sends one plain test email and reports exactly what happened. Never throws. */
    MailDiagnosticResponse sendTestEmail(String toEmail);
}
