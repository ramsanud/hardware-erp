package com.hardware.erp.notification.whatsapp;

/**
 * CR-080 - turns a phone number and a message into something that opens a
 * WhatsApp chat.
 *
 * Deliberately a separate contract from {@link com.hardware.erp.notification.service.NotificationProvider}:
 * that one <i>sends</i> (the CR-056 Cloud API path, which needs a connected
 * Business account); this one <i>prepares a link the human opens</i>. The
 * only implementation today is {@link ManualWhatsAppService}. A future
 * implementation could, for a shop that IS connected, return a deep link
 * into an already-sent conversation - which is why callers depend on this
 * interface and never on the manual class.
 */
public interface WhatsAppService {

    /**
     * @param phoneNumber any format {@link com.hardware.erp.common.util.PhoneNumberNormalizer}
     *                    accepts; refused with a BusinessException otherwise
     * @param message     plain text, may be blank (the chat simply opens empty)
     * @return an absolute https URL safe to hand to {@code window.open}
     */
    String generateChatUrl(String phoneNumber, String message);
}
