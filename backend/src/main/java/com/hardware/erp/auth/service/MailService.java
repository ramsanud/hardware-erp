package com.hardware.erp.auth.service;

public interface MailService {

    void sendPasswordResetLink(String toEmail, String recipientName, String resetUrl);
}
