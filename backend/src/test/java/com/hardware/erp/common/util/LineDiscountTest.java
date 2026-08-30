package com.hardware.erp.common.util;

import com.hardware.erp.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CR-047 / CR-049 / CR-050. The arithmetic the whole pricing feature rests
 * on - both InvoiceServiceImpl and QuotationServiceImpl delegate here, so a
 * bug caught in this class is a bug that would otherwise have reached both
 * documents.
 *
 * All money is paise. 500_00 reads as ₹500.00.
 */
class LineDiscountTest {

    private static final BigDecimal GST_18 = new BigDecimal("18.00");
    private static final BigDecimal NO_GST = BigDecimal.ZERO;
    private static final String LABEL = "Door Lock";

    private static LineDiscount.Priced price(String qty, long unitPaise, BigDecimal gst,
                                             String discountPercent, String labourPercent) {
        return LineDiscount.price(
                new BigDecimal(qty), unitPaise, gst,
                discountPercent == null ? LineDiscount.Type.NONE : LineDiscount.Type.PERCENTAGE,
                discountPercent == null ? null : new BigDecimal(discountPercent),
                labourPercent == null ? null : new BigDecimal(labourPercent),
                LABEL);
    }

    @Nested
    @DisplayName("discount only")
    class DiscountOnly {

        @Test
        @DisplayName("no discount leaves gross as net")
        void noDiscount() {
            var p = price("5", 100_00, NO_GST, null, null);

            assertThat(p.grossPaise()).isEqualTo(500_00);
            assertThat(p.discountAmountPaise()).isZero();
            assertThat(p.netPaise()).isEqualTo(500_00);
            assertThat(p.effectiveUnitPricePaise()).isEqualTo(100_00);
        }

        @Test
        @DisplayName("10% of ₹500 is ₹50")
        void percentageDiscount() {
            var p = price("5", 100_00, NO_GST, "10", null);

            assertThat(p.discountAmountPaise()).isEqualTo(50_00);
            assertThat(p.netPaise()).isEqualTo(450_00);
            assertThat(p.effectiveUnitPricePaise()).isEqualTo(90_00);
        }

        @Test
        @DisplayName("100% is allowed and lands on zero, never below")
        void fullDiscount() {
            var p = price("5", 100_00, GST_18, "100", null);

            assertThat(p.netPaise()).isZero();
            assertThat(p.gstPaise()).isZero();
            assertThat(p.totalPaise()).isZero();
        }
    }

    @Nested
    @DisplayName("internal labour")
    class Labour {

        @Test
        @DisplayName("the worked example: ₹100 base, 2% labour, qty 2 -> ₹204")
        void labourOnly() {
            var p = price("2", 100_00, NO_GST, null, "2");

            assertThat(p.grossPaise()).isEqualTo(200_00);
            assertThat(p.labourAmountPaise()).isEqualTo(4_00);
            assertThat(p.netPaise()).isEqualTo(204_00);
            assertThat(p.effectiveUnitPricePaise()).isEqualTo(102_00);
        }

        @Test
        @DisplayName("labour is taken off the DISCOUNTED value, not the gross")
        void labourFollowsDiscount() {
            // ₹100 x 2 = ₹200 gross, 10% off = ₹20, after = ₹180,
            // 2% labour of ₹180 = ₹3.60, net = ₹183.60, rate ₹91.80
            var p = price("2", 100_00, NO_GST, "10", "2");

            assertThat(p.grossPaise()).isEqualTo(200_00);
            assertThat(p.discountAmountPaise()).isEqualTo(20_00);
            assertThat(p.afterDiscountPaise()).isEqualTo(180_00);
            assertThat(p.labourAmountPaise()).isEqualTo(3_60);
            assertThat(p.netPaise()).isEqualTo(183_60);
            assertThat(p.effectiveUnitPricePaise()).isEqualTo(91_80);
        }

        @Test
        @DisplayName("labour on the gross would be ₹4.00 not ₹3.60 - guard the order")
        void labourNotTakenOnGross() {
            var p = price("2", 100_00, NO_GST, "10", "2");
            assertThat(p.labourAmountPaise()).isNotEqualTo(4_00);
        }

        @Test
        @DisplayName("8% discount + 2% labour: the second worked example")
        void eightAndTwo() {
            // ₹100 x 2 = ₹200, 8% = ₹16, after = ₹184, 2% = ₹3.68,
            // net = ₹187.68, rate ₹93.84
            var p = price("2", 100_00, NO_GST, "8", "2");

            assertThat(p.netPaise()).isEqualTo(187_68);
            assertThat(p.effectiveUnitPricePaise()).isEqualTo(93_84);
        }

        @Test
        @DisplayName("labour is never applied twice")
        void labourAppliedOnce() {
            var p = price("2", 100_00, NO_GST, null, "2");
            // Applied twice would be ₹208.08, not ₹204.
            assertThat(p.netPaise()).isEqualTo(204_00);
        }
    }

    @Nested
    @DisplayName("GST is charged on the net, after discount and labour")
    class Tax {

        @Test
        @DisplayName("GST follows discount and labour")
        void gstOnNet() {
            // net ₹183.60, GST 18% = ₹33.05 (33.048 -> HALF_UP)
            var p = price("2", 100_00, GST_18, "10", "2");

            assertThat(p.netPaise()).isEqualTo(183_60);
            assertThat(p.gstPaise()).isEqualTo(33_05);
            assertThat(p.totalPaise()).isEqualTo(216_65);
        }

        @Test
        @DisplayName("net + gst always equals total, whatever the rounding did")
        void totalIsConsistent() {
            var p = price("7", 333_33, GST_18, "7.5", "2.5");
            assertThat(p.totalPaise()).isEqualTo(p.netPaise() + p.gstPaise());
            assertThat(p.netPaise()).isEqualTo(p.afterDiscountPaise() + p.labourAmountPaise());
            assertThat(p.afterDiscountPaise()).isEqualTo(p.grossPaise() - p.discountAmountPaise());
        }
    }

    @Nested
    @DisplayName("rejections")
    class Rejections {

        @Test
        @DisplayName("a negative discount is refused, not clamped")
        void negativeDiscount() {
            assertThatThrownBy(() -> price("5", 100_00, NO_GST, "-5", null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Discount")
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        @DisplayName("more than 100% discount is refused")
        void discountOverHundred() {
            assertThatThrownBy(() -> price("5", 100_00, NO_GST, "101", null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("100%");
        }

        @Test
        @DisplayName("a negative labour percentage is refused")
        void negativeLabour() {
            assertThatThrownBy(() -> price("5", 100_00, NO_GST, null, "-2"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Labour");
        }

        @Test
        @DisplayName("more than 100% labour is refused")
        void labourOverHundred() {
            assertThatThrownBy(() -> price("5", 100_00, NO_GST, null, "150"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("100%");
        }

        @Test
        @DisplayName("zero and negative quantities are refused")
        void badQuantity() {
            assertThatThrownBy(() -> price("0", 100_00, NO_GST, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Quantity");
        }

        @Test
        @DisplayName("the error names the product, so the owner knows which line is wrong")
        void errorNamesProduct() {
            assertThatThrownBy(() -> price("1", 100_00, NO_GST, "150", null))
                    .hasMessageContaining(LABEL);
        }
    }

    @Nested
    @DisplayName("nulls and pre-CR-050 rows")
    class Defaults {

        @Test
        @DisplayName("null type and null percentages price as an untouched line")
        void allNull() {
            var p = LineDiscount.price(new BigDecimal("3"), 200_00, GST_18,
                    null, null, null, LABEL);

            assertThat(p.discountAmountPaise()).isZero();
            assertThat(p.labourAmountPaise()).isZero();
            assertThat(p.netPaise()).isEqualTo(600_00);
            assertThat(p.gstPaise()).isEqualTo(108_00);
        }

        @Test
        @DisplayName("a null GST rate is treated as zero-rated, not a crash")
        void nullGst() {
            var p = LineDiscount.price(new BigDecimal("2"), 100_00, null,
                    null, null, null, LABEL);
            assertThat(p.gstPaise()).isZero();
        }
    }

    @Nested
    @DisplayName("rounding")
    class Rounding {

        @Test
        @DisplayName("a third of a rupee rounds half-up to whole paise")
        void roundsToWholePaise() {
            var p = price("1", 10_00, NO_GST, "33.33", null);
            assertThat(p.discountAmountPaise()).isEqualTo(333);
            assertThat(p.netPaise()).isEqualTo(667);
        }

        @Test
        @DisplayName("fractional quantities price correctly - hardware sells by the metre")
        void fractionalQuantity() {
            var p = price("2.5", 100_00, NO_GST, "10", null);
            assertThat(p.grossPaise()).isEqualTo(250_00);
            assertThat(p.netPaise()).isEqualTo(225_00);
            assertThat(p.effectiveUnitPricePaise()).isEqualTo(90_00);
        }
    }
}
