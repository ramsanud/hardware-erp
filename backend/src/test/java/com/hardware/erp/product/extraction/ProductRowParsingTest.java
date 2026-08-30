package com.hardware.erp.product.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-PROD-004.
 *
 * A shop uploaded a sheet whose headers were the API field names -
 * productCode, productName, gstRatePercent - and every row failed with
 * "Product name is missing". The values were all present; the header lookup
 * was matching on human-readable aliases ("product name", with a space)
 * against a key that had only been lowercased, so "productCode" became
 * "productcode" and matched nothing.
 *
 * Only single-word headers (unit, mrp) happened to work, which made it look
 * like a data problem rather than a parsing one.
 *
 * These tests pin every header spelling a person or a system might
 * reasonably produce.
 */
class ProductRowParsingTest {

    private static Map<String, String> row(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Nested
    @DisplayName("header spellings that must all resolve to the same field")
    class HeaderSpellings {

        @Test
        @DisplayName("camelCase - the spelling from the reported upload")
        void camelCase() {
            ExtractedProductRow parsed = ProductRowParsing.parse(2, row(
                    "productCode", "PRD-1001",
                    "productName", "Hex Bolt M10 x 50mm",
                    "categoryCode", "FASTENERS",
                    "brandCode", "SHAKTI",
                    "unit", "PCS",
                    "hsnCode", "73181500",
                    "gstRatePercent", "18",
                    "purchasePrice", "12",
                    "sellingPrice", "18",
                    "mrp", "22",
                    "reorderLevel", "50"));

            assertThat(parsed.getErrors()).isEmpty();
            assertThat(parsed.getProductCode()).isEqualTo("PRD-1001");
            assertThat(parsed.getProductName()).isEqualTo("Hex Bolt M10 x 50mm");
            assertThat(parsed.getCategoryName()).isEqualTo("FASTENERS");
            assertThat(parsed.getBrandName()).isEqualTo("SHAKTI");
            assertThat(parsed.getUnit()).isEqualTo("PCS");
            assertThat(parsed.getHsnCode()).isEqualTo("73181500");
            assertThat(parsed.getGstRatePercent()).isEqualByComparingTo("18");
            assertThat(parsed.getPurchasePriceRupees()).isEqualByComparingTo("12");
            assertThat(parsed.getSellingPriceRupees()).isEqualByComparingTo("18");
            assertThat(parsed.getMrpRupees()).isEqualByComparingTo("22");
            assertThat(parsed.getReorderLevel()).isEqualByComparingTo("50");
        }

        @Test
        @DisplayName("spaced and title-cased - what a hand-made sheet looks like")
        void spacedTitleCase() {
            ExtractedProductRow parsed = ProductRowParsing.parse(2, row(
                    "Product Code", "PRD-1002",
                    "Product Name", "Hammer",
                    "Category", "TOOLS",
                    "Brand", "TAPARIA",
                    "Unit", "PCS",
                    "HSN Code", "8205",
                    "GST %", "18",
                    "Purchase Price", "100",
                    "Selling Price", "150",
                    "MRP", "180"));

            assertThat(parsed.getErrors()).isEmpty();
            assertThat(parsed.getProductName()).isEqualTo("Hammer");
            assertThat(parsed.getCategoryName()).isEqualTo("TOOLS");
            assertThat(parsed.getSellingPriceRupees()).isEqualByComparingTo("150");
        }

        @Test
        @DisplayName("snake_case and SCREAMING_CASE - what an export from another system looks like")
        void snakeAndScreamingCase() {
            ExtractedProductRow parsed = ProductRowParsing.parse(2, row(
                    "product_code", "PRD-1003",
                    "PRODUCT_NAME", "Screwdriver",
                    "category_name", "TOOLS",
                    "BRAND_NAME", "BOSCH",
                    "unit", "PCS",
                    "gst_rate_percent", "18",
                    "purchase_price", "40",
                    "selling_price", "60",
                    "mrp", "75"));

            assertThat(parsed.getErrors()).isEmpty();
            assertThat(parsed.getProductName()).isEqualTo("Screwdriver");
            assertThat(parsed.getBrandName()).isEqualTo("BOSCH");
        }

        @Test
        @DisplayName("stray whitespace around a header does not break the match")
        void paddedHeaders() {
            ExtractedProductRow parsed = ProductRowParsing.parse(2, row(
                    "  productName  ", "Chisel",
                    " unit ", "PCS",
                    "gst", "18",
                    "purchase price", "30",
                    "selling price", "45",
                    "mrp", "50"));

            assertThat(parsed.getErrors()).isEmpty();
            assertThat(parsed.getProductName()).isEqualTo("Chisel");
        }
    }

    @Nested
    @DisplayName("genuine problems are still reported")
    class RealErrors {

        @Test
        @DisplayName("a truly missing name is still an error - the fix must not mask real gaps")
        void missingNameStillFails() {
            ExtractedProductRow parsed = ProductRowParsing.parse(2, row(
                    "productCode", "PRD-1004",
                    "unit", "PCS",
                    "gstRatePercent", "18",
                    "purchasePrice", "10",
                    "sellingPrice", "15",
                    "mrp", "20"));

            assertThat(parsed.getErrors()).contains("Product name is missing");
        }

        @Test
        @DisplayName("a missing unit is still an error")
        void missingUnitStillFails() {
            ExtractedProductRow parsed = ProductRowParsing.parse(2, row(
                    "productName", "Nut M10",
                    "gstRatePercent", "18",
                    "purchasePrice", "1",
                    "sellingPrice", "2",
                    "mrp", "3"));

            assertThat(parsed.getErrors()).contains("Unit is missing");
        }

        @Test
        @DisplayName("an unsupported column is ignored rather than failing the row")
        void unknownColumnsIgnored() {
            ExtractedProductRow parsed = ProductRowParsing.parse(2, row(
                    "productName", "Bolt",
                    "unit", "PCS",
                    "gstRatePercent", "18",
                    "purchasePrice", "5",
                    "sellingPrice", "8",
                    "mrp", "10",
                    "openingStock", "500",
                    "maxStockLevel", "2000",
                    "rackNumber", "R1-A",
                    "status", "ACTIVE"));

            assertThat(parsed.getErrors()).isEmpty();
            assertThat(parsed.getProductName()).isEqualTo("Bolt");
        }
    }
}
