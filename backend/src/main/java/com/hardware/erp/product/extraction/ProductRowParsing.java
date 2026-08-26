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
        Map<String, String> byLowerKey = new HashMap<>();
        raw.forEach((key, value) -> {
            if (key != null) byLowerKey.put(key.trim().toLowerCase(Locale.ROOT), value);
        });

        ExtractedProductRow row = new ExtractedProductRow(rowNumber);
        row.setProductName(sanitize(text(byLowerKey, "product name", "product")));
        row.setProductCode(sanitize(text(byLowerKey, "product code", "code", "sku")));
        row.setCategoryName(sanitize(text(byLowerKey, "category")));
        row.setBrandName(sanitize(text(byLowerKey, "brand")));
        row.setUnit(sanitize(text(byLowerKey, "unit")));
        row.setHsnCode(sanitize(text(byLowerKey, "hsn code", "hsn")));

        if (row.getProductName() == null || row.getProductName().isBlank()) {
            row.getErrors().add("Product name is missing");
        }
        if (row.getUnit() == null || row.getUnit().isBlank()) {
            row.getErrors().add("Unit is missing");
        }

        row.setGstRatePercent(number(byLowerKey, row, "gst %", "gst", "gst rate", "tax %"));
        row.setPurchasePriceRupees(number(byLowerKey, row, "purchase price", "cost price"));
        row.setSellingPriceRupees(number(byLowerKey, row, "selling price", "price"));
        row.setMrpRupees(number(byLowerKey, row, "mrp"));
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

    private static String text(Map<String, String> byLowerKey, String... keys) {
        for (String key : keys) {
            String value = byLowerKey.get(key);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static BigDecimal number(Map<String, String> byLowerKey, ExtractedProductRow row, String... keys) {
        String raw = text(byLowerKey, keys);
        if (raw == null) {
            row.getErrors().add(capitalize(keys[0]) + " is missing");
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
