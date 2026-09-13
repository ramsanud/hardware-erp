package com.hardware.erp.notification.whatsapp;

import com.hardware.erp.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CR-080 - the wa.me link itself.
 *
 * The message assertions decode the URL back and compare to the original
 * rather than pinning a hand-written encoded string: a test that hard-codes
 * "%E0%AE%B5..." proves only that the author typed the same bytes twice. The
 * round trip is what a WhatsApp client does, so it is what is asserted.
 */
class ManualWhatsAppServiceTest {

    private final WhatsAppService service = new ManualWhatsAppService();

    /** What the receiving end sees: the ?text= parameter, percent-decoded. */
    private static String textParamOf(String url) {
        String query = URI.create(url).getRawQuery();
        assertThat(query).startsWith("text=");
        return URLDecoder.decode(query.substring("text=".length()), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the shape is https://wa.me/<digits, no plus>?text=<encoded>")
    void shape() {
        String url = service.generateChatUrl("+91 98765 43210", "Hello");

        assertThat(url).startsWith("https://wa.me/919876543210?text=");
        assertThat(URI.create(url).getHost()).isEqualTo("wa.me");
        assertThat(URI.create(url).getPath()).isEqualTo("/919876543210");
        assertThat(textParamOf(url)).isEqualTo("Hello");
    }

    @Test
    @DisplayName("a bare Indian mobile - how every customer is stored - is dialled as +91")
    void bareIndianMobile() {
        assertThat(service.generateChatUrl("9876543210", "x")).startsWith("https://wa.me/919876543210?");
    }

    @Test
    @DisplayName("the URL is parseable by java.net.URI, so nothing raw was concatenated into it")
    void isAWellFormedUri() {
        String url = service.generateChatUrl("9876543210", "Balance: ₹7,450 & thanks? see /invoice #12 'ok'");
        // URI.create throws on anything not percent-encoded where it must be.
        assertThat(URI.create(url).isAbsolute()).isTrue();
        assertThat(url).doesNotContain(" ").doesNotContain("₹").doesNotContain("'");
    }

    @ParameterizedTest(name = "round-trips: {0}")
    @DisplayName("every character class a real message carries survives the round trip unchanged")
    @ValueSource(strings = {
            "Hello Ravi 👋\nInvoice total: ₹12,450",                 // emoji, rupee, newline
            "வணக்கம் ரவி, உங்கள் பில் தயார்",                         // Tamil
            "Ravi's Hardware & Sons",                                 // apostrophe and ampersand
            "Is the balance ₹7,450?",                                 // question mark
            "See invoice INV-1024/2026 #ref",                         // slash and hash
            "Discount 10% + GST 18% = total",                         // percent and a literal plus
            "Line one\r\nLine two\ttabbed",                            // CR LF and tab
            "Tools & Hand Tools; Paints=Adhesives; a,b",              // separators that mean things in queries
            "\"Quoted\" text and <angle> brackets",                   // characters that would matter as HTML
    })
    void roundTrip(String message) {
        String url = service.generateChatUrl("9876543210", message);
        assertThat(textParamOf(url)).isEqualTo(message);
    }

    @Test
    @DisplayName("spaces become %20, not '+', so a real '+' in the text is never confused with one")
    void spacesArePercentTwenty() {
        String url = service.generateChatUrl("9876543210", "a b+c");
        assertThat(url).endsWith("?text=a%20b%2Bc");
        assertThat(textParamOf(url)).isEqualTo("a b+c");
    }

    @Test
    @DisplayName("an empty message opens the chat with nothing typed - no dangling ?text=")
    void emptyMessage() {
        assertThat(service.generateChatUrl("9876543210", "")).isEqualTo("https://wa.me/919876543210");
        assertThat(service.generateChatUrl("9876543210", null)).isEqualTo("https://wa.me/919876543210");
        assertThat(service.generateChatUrl("9876543210", "   ")).isEqualTo("https://wa.me/919876543210");
    }

    @Test
    @DisplayName("an invalid number is refused before any URL exists")
    void invalidNumberIsRefused() {
        assertThatThrownBy(() -> service.generateChatUrl("12345", "Hello"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.generateChatUrl("", "Hello"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("an international customer is dialled in their own country")
    void internationalNumber() {
        assertThat(service.generateChatUrl("+44 20 7946 0958", "Hi"))
                .startsWith("https://wa.me/442079460958?text=");
    }
}
