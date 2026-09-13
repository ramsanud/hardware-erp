package com.hardware.erp.notification.whatsapp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * CR-080 - assembles the wa.me link. Package-private construction, one
 * static method, so there is exactly one place the encoding can be wrong.
 */
final class WhatsAppUrlBuilder {

    static final String BASE = "https://wa.me/";

    private WhatsAppUrlBuilder() {
    }

    /**
     * @param e164Digits the number with no '+', already validated
     * @param message    plain text; blank means "open the chat, type nothing"
     */
    static String chatUrl(String e164Digits, String message) {
        if (message == null || message.isBlank()) {
            return BASE + e164Digits;
        }
        return BASE + e164Digits + "?text=" + encode(message);
    }

    /**
     * URLEncoder is a form encoder: it writes a space as '+', which a query
     * string reader may or may not turn back into a space, and it leaves '*'
     * alone. Rewriting '+' to '%20' makes the result plain RFC 3986 percent-
     * encoding, which every WhatsApp client decodes the same way - and a real
     * '+' in the message was already written as '%2B', so nothing collides.
     * Tamil, emoji and every reserved character ('&', '?', '/', '#', '\'')
     * come out as percent-encoded UTF-8 and arrive unchanged.
     */
    static String encode(String message) {
        return URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
