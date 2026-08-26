package com.hardware.erp.purchase.upload;

import com.hardware.erp.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentUploadValidationTest {

    @Test
    void rejectsAFilenameContainingADoubleQuote() {
        // BUG-PUR-004: this filename is later echoed into a Content-Disposition response
        // header on download - an unescaped quote could break out of the quoted filename
        // parameter and inject additional header content.
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil\".csv", "text/csv", "Product Name,Quantity,Unit,Unit Price\n".getBytes());

        assertThatThrownBy(() -> DocumentUploadValidation.validateAndGetExtension(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid file name");
    }

    @Test
    void rejectsAFilenameContainingCarriageReturnOrLineFeed() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.csv\r\nX-Injected: pwned", "text/csv", "Product Name\n".getBytes());

        assertThatThrownBy(() -> DocumentUploadValidation.validateAndGetExtension(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid file name");
    }

    @Test
    void safeContentTypeIsDerivedFromExtensionNeverFromClientInput() {
        // BUG-PUR-004: the multipart Content-Type header is fully attacker-controlled. A
        // "bill.csv" declared as text/html and later served with Content-Disposition: inline
        // would render as live HTML (stored XSS). The served content type must only ever
        // come from the extension this class already validated, never from that header.
        assertThat(DocumentUploadValidation.safeContentType("csv")).isEqualTo("text/csv");
        assertThat(DocumentUploadValidation.safeContentType("xlsx"))
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(DocumentUploadValidation.safeContentType("html")).isEqualTo("application/octet-stream");
    }
}
