package com.hardware.erp.auth.service;

import com.hardware.erp.auth.entity.EmailOtpPurpose;

/**
 * CR-078 - sends a one-time code by email. Separate from {@link MailService}
 * (the reset link) because the two are mocked differently in tests: a test
 * that needs the code a user "received" swaps this bean for a recorder.
 */
public interface OtpMailService {

    /**
     * @param recipientName may be null before an account exists (registration);
     *                      the wording degrades to "Hello," rather than "Hello null,"
     */
    void sendCode(String toEmail, String recipientName, EmailOtpPurpose purpose, String code, int validMinutes);
}
