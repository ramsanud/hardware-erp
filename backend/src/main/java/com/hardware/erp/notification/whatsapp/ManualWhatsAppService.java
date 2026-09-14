package com.hardware.erp.notification.whatsapp;

import com.hardware.erp.common.util.PhoneNumberNormalizer;
import org.springframework.stereotype.Service;

/**
 * CR-080 - the free path. Produces a {@code https://wa.me/...} link that the
 * official WhatsApp app or WhatsApp Web opens on the customer's chat with the
 * text pre-filled. The person then presses Send; nothing here ever sends.
 *
 * No credential, no session, no automation, no storage - and therefore no
 * logging either: the URL this returns <i>is</i> the customer's number plus
 * the message, so it is never written to a log at any level.
 */
@Service
public class ManualWhatsAppService implements WhatsAppService {

    @Override
    public String generateChatUrl(String phoneNumber, String message) {
        return WhatsAppUrlBuilder.chatUrl(PhoneNumberNormalizer.toE164Digits(phoneNumber), message);
    }
}
