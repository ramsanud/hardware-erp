package com.hardware.erp.auth.service;

public interface MailService {

    /**
     * @param code CR-078 - the six-digit reset code that travels in the same
     *             email as the link; null when none could be issued (a code
     *             from moments ago is still live), in which case the mail says so
     */
    void sendPasswordResetLink(String toEmail, String recipientName, String resetUrl, String code);
}
