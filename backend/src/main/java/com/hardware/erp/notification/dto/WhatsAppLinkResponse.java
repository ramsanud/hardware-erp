package com.hardware.erp.notification.dto;

/**
 * CR-080 - a ready-to-open WhatsApp chat link.
 *
 * {@code toMobileNo} is the number as the shop entered it, for display beside
 * the button; {@code message} is the plain text that will appear pre-filled,
 * so a UI can show it without decoding the URL. Nothing here is ever stored.
 */
public record WhatsAppLinkResponse(
        String url,
        String toMobileNo,
        String message
) {
}
