package com.hardware.erp.product.extraction;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared column-interpretation logic for both product CSV and Excel
 * extraction (CR-036) - mirrors purchase.extraction.RowParsing, including
 * its control-character sanitization (BUG-PUR-002: an unsanitized field
 * used in a later database query crashed with a PostgreSQL null-byte
 * error) applied here from the start rather than found live a second time.
 */
final class ProductRowParsing {

    private ProductRowParsing() {
    }

    static ExtractedProductRow parse(int rowNumber, Map<String, String> raw) {
        // BUG-PROD-004: keys were only trimmed and lowercased, so a header
        // spelled "productCode" became "productcode" and never matched the
        // alias "product code". Every camelCase and snake_case sheet failed
        // with "Product name is missing" while the value sat right there.
        // Both sides are now reduced to letters and digits only, so
        // "Product Code", "productCode", "product_code" and "PRODUCT-CODE"
        // are one key.
        Map<String, String> byLowerKey = new HashMap<>();
        raw.forEach((key, value) -> {
            if (key != null) byLowerKey.put(normaliseKey(key), value);
        });

        ExtractedProductRow row = new ExtractedProductRow(rowNumber);
        row.setProductName(sanitize(text(byLowerKey, "product name", "product", "item name", "description")));
        row.setProductCode(sanitize(text(byLowerKey, "product code", "code", "sku", "item code")));
        // A sheet exported from another system identifies a category by its
        // CODE; a hand-made one writes the name. Both land in categoryName -
        // ProductImportServiceImpl resolves either against the master.
        row.setCategoryName(sanitize(text(byLowerKey, "category", "category code", "category name")));
        row.setBrandName(sanitize(text(byLowerKey, "brand", "brand code", "brand name")));
        row.setUnit(sanitize(text(byLowerKey, "unit", "uom")));
        row.setHsnCode(sanitize(text(byLowerKey, "hsn code", "hsn", "hsn sac")));

        if (row.getProductName() == null || row.getProductName().isBlank()) {
            row.getErrors().add("Product name is missing");
        }
        if (row.getUnit() == null || row.getUnit().isBlank()) {
            row.getErrors().add("Unit is missing");
        }

        // The first argument is the label used in the error message, so a
        // missing GST column reads "GST rate is missing" rather than the old
        // "Gst % is missing" - which named an alias, not a concept.
        row.setGstRatePercent(number(byLowerKey, row, "GST rate",
                "gst %", "gst", "gst rate", "gst rate percent", "tax %", "tax rate"));
        row.setPurchasePriceRupees(number(byLowerKey, row, "Purchase price",
                "purchase price", "cost price", "cost", "buy price"));
        row.setSellingPriceRupees(number(byLowerKey, row, "Selling price",
                "selling price", "price", "sale price", "rate"));
        row.setMrpRupees(number(byLowerKey, row, "MRP", "mrp"));
        row.setMinimumStock(numberOptional(byLowerKey, "minimum stock", "min stock"));
        row.setReorderLevel(numberOptional(byLowerKey, "reorder level", "reorder"));

        return row;
    }

    private static String sanitize(String value) {
        if (value == null) return null;
        String cleaned = value.chars()
                .filter(c -> c == '\t' || c == ' ' || !Character.isISOControl(c))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Reduces a header to letters and digits, lowercased, so every plausible
     * spelling of the same column collapses to one key. Applied to the
     * spreadsheet's headers AND to the aliases below, so the two can never
     * drift apart.
     */
    private static String normaliseKey(String key) {
        StringBuilder out = new StringBuilder(key.length());
        for (char c : key.toCharArray()) {
            if (Character.isLetterOrDigit(c)) out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    private static String text(Map<String, String> byLowerKey, String... keys) {
        for (String key : keys) {
            String value = byLowerKey.get(normaliseKey(key));
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static BigDecimal number(Map<String, String> byLowerKey, ExtractedProductRow row,
                                     String label, String... keys) {
        String raw = text(byLowerKey, keys);
        if (raw == null) {
            row.getErrors().add(label + " is missing");
            return null;
        }
        String cleaned = raw.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isBlank() || cleaned.equals("-") || cleaned.equals(".")) {
            row.getErrors().add(capitalize(keys[0]) + " \"" + raw + "\" is not a valid number");
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            row.getErrors().add(capitalize(keys[0]) + " \"" + raw + "\" is not a valid number");
            return null;
        }
    }

    /** Minimum stock / reorder level default to zero rather than being required - most bulk imports do not carry them. */
    private static BigDecimal numberOptional(Map<String, String> byLowerKey, String... keys) {
        String raw = text(byLowerKey, keys);
        if (raw == null) return BigDecimal.ZERO;
        String cleaned = raw.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isBlank() || cleaned.equals("-") || cleaned.equals(".")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
