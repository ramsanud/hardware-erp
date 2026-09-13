package com.hardware.erp.notification.whatsapp;

import org.springframework.stereotype.Component;

/**
 * CR-080 - the text a customer reads. One class, so an invoice message says
 * the same thing whether it was opened from the invoice page or a customer's
 * outstanding list, and so a wording change is one edit.
 *
 * Amounts arrive already formatted ({@code "12,450.00"}) - the same
 * {@code …Display} strings the DTOs give the invoice page - so what the
 * customer reads is what the owner saw. Plain text only: WhatsApp renders no
 * HTML, and every value here (a customer's name, a remark) is treated as the
 * literal characters it is. The rupee sign is a character, not "Rs".
 *
 * Deliberately separate from the shorter texts in NotificationServiceImpl
 * that the automatic Cloud API path sends: those are constrained by Meta's
 * template rules and asserted word-for-word by tests since CR-027, and a
 * message a person is about to read and press Send on can afford to be
 * warmer and more complete.
 */
@Component
public class WhatsAppMessageTemplates {

    private static final String WAVE = "👋";

    public String invoice(String shopName, String customerName, String invoiceNumber,
                          String total, String paid, String balance) {
        return """
                Hello %s %s

                Thank you for choosing %s.

                Invoice No: %s
                Total: ₹%s
                Paid: ₹%s
                Balance: ₹%s

                Please contact us if you have any questions.

                Thank you,
                %s""".formatted(customerName, WAVE, shopName, invoiceNumber, total, paid, balance, shopName);
    }

    public String quotation(String shopName, String customerName, String quotationNumber,
                            String total, String validUntil) {
        return """
                Hello %s %s

                Please find your quotation from %s.

                Quotation No: %s
                Total: ₹%s
                Valid Until: %s

                Please contact us if you have any questions.

                Thank you,
                %s""".formatted(customerName, WAVE, shopName, quotationNumber, total, validUntil, shopName);
    }

    public String paymentReceipt(String shopName, String customerName, String invoiceNumber,
                                 String amountReceived, String paymentDate, String remainingBalance) {
        return """
                Hello %s %s

                Payment received successfully.

                Invoice No: %s
                Amount Received: ₹%s
                Date: %s
                Remaining Balance: ₹%s

                Thank you,
                %s""".formatted(customerName, WAVE, invoiceNumber, amountReceived, paymentDate, remainingBalance, shopName);
    }

    public String paymentReminder(String shopName, String customerName, String invoiceNumber,
                                  String outstanding) {
        return """
                Hello %s %s

                This is a friendly reminder regarding your outstanding balance.

                Invoice No: %s
                Outstanding Amount: ₹%s

                Please contact us if you need any clarification.

                Thank you,
                %s""".formatted(customerName, WAVE, invoiceNumber, outstanding, shopName);
    }

    public String customerGreeting(String shopName, String customerName) {
        return """
                Hello %s %s

                Thank you for choosing %s.

                How can we help you today?""".formatted(customerName, WAVE, shopName);
    }
}
