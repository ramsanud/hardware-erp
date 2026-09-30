package com.hardware.erp.notification.service;

import com.hardware.erp.notification.dto.SmsDiagnosticResponse;

/** CR-085 - one synchronous test SMS, reporting SENT / LOGGED_ONLY / FAILED with the provider's own reason. */
public interface SmsDiagnosticService {
    SmsDiagnosticResponse sendTestSms(String toMobileNo);
}
