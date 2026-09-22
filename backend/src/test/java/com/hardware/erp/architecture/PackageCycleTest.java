package com.hardware.erp.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-BE-008 (BUG-BE-006 on its branch; renumbered at consolidation). The one cycle the 2026-09-02 architecture audit found:
 * {@code product} imported {@code invoice} (for price history) while
 * {@code invoice} imports {@code product} (a line item is a product). It was
 * broken with {@code ProductSaleHistoryProvider}; this test keeps it broken
 * by reading the sources, so it needs no library and runs in the unit tier.
 *
 * Product is the upstream module of the sales chain - quotation, invoice,
 * sales order, delivery challan and credit note all point at it - so it must
 * point at none of them.
 */
class PackageCycleTest {

    private static final Path PRODUCT = Path.of("src/main/java/com/hardware/erp/product");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+com\\.hardware\\.erp\\.(\\w+)\\.", Pattern.MULTILINE);
    private static final List<String> DOWNSTREAM = List.of(
            "invoice", "quotation", "salesorder", "deliverychallan", "creditnote", "payment");

    @Test
    @DisplayName("the product module imports none of the sales modules that depend on it")
    void productDoesNotImportItsDependants() throws IOException {
        try (Stream<Path> files = Files.walk(PRODUCT)) {
            List<String> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> importsFrom(path).stream()
                            .filter(DOWNSTREAM::contains)
                            .map(module -> PRODUCT.relativize(path) + " -> " + module))
                    .toList();
            assertThat(offenders)
                    .as("product must not depend on a module that depends on product")
                    .isEmpty();
        }
    }

    private static List<String> importsFrom(Path file) {
        try {
            Matcher matcher = IMPORT.matcher(Files.readString(file));
            return matcher.results().map(result -> result.group(1)).toList();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
