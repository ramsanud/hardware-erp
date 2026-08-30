package com.hardware.erp.common.util;

import com.hardware.erp.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CR-047. These are the arithmetic rules the whole feature rests on - both
 * InvoiceServiceImpl and QuotationServiceImpl delegate here, so a bug caught
 * in this class is a bug that would otherwise have reached both documents.
 *
 * All money is paise. 500_00 reads as ₹500.00.
 */
class LineDiscountTest {

    private static final BigDecimal GST_18 = new BigDecimal("18.00");
    private static final BigDecimal NO_GST = BigDecimal.ZERO;
    private static final String LABEL = "Door Lock";

    private static LineDiscount.Priced price(String qty, long unitPaise, BigDecimal gst,
                                             LineDiscount.Type type, String percent, Long amountPaise) {
        return LineDiscount.price(new BigDecimal(qty), unitPaise, gst, type,
                percent == null ? null : new BigDecimal(percent), amountPaise, LABEL);
    }

    @Nested
    @DisplayName("the four cases from the specification")
    class SpecCases {

        @Test
        @DisplayName("case 1 - no discount leaves gross as net")
        void noDiscount() {
            var p = price("5", 100_00, NO_GST, LineDiscount.Type.NONE, null, null);

            assertThat(p.grossPaise()).isEqualTo(500_00);
            assertThat(p.discountAmountPaise()).isZero();
            assertThat(p.netPaise()).isEqualTo(500_00);
            assertThat(p.totalPaise()).isEqualTo(500_00);
        }

        @Test
        @DisplayName("case 2 - 10% of ₹500 is ₹50")
        void percentageDiscount() {
            var p = price("5", 100_00, NO_GST, LineDiscount.Type.PERCENTAGE, "10", null);

            assertThat(p.grossPaise()).isEqualTo(500_00);
            assertThat(p.discountAmountPaise()).isEqualTo(50_00);
            assertThat(p.netPaise()).isEqualTo(450_00);
        }

        @Test
        @DisplayName("case 3 - a fixed ₹50 off the same line")
        void fixedDiscount() {
            var p = price("5", 100_00, NO_GST, LineDiscount.Type.AMOUNT, null, 50_00L);

            assertThat(p.discountAmountPaise()).isEqualTo(50_00);
            assertThat(p.netPaise()).isEqualTo(450_00);
        }

        @Test
        @DisplayName("case 4 - 100% is allowed and lands on zero, never below")
        void fullDiscount() {
            var p = price("5", 100_00, GST_18, LineDiscount.Type.PERCENTAGE, "100", null);

            assertThat(p.discountAmountPaise()).isEqualTo(500_00);
            assertThat(p.netPaise()).isZero();
            // No GST is chargeable on a free-of-charge line.
            assertThat(p.gstPaise()).isZero();
            assertThat(p.totalPaise()).isZero();
        }
    }

    @Nested
    @DisplayName("GST is charged on the discounted value, not the gross")
    class TaxOrdering {

        @Test
        @DisplayName("the worked example from the spec")
        void gstFollowsDiscount() {
            // 5 x ₹500 = ₹2500 gross, 10% off = ₹250, net ₹2250, GST 18% = ₹405
            var p = price("5", 500_00, GST_18, LineDiscount.Type.PERCENTAGE, "10", null);

            assertThat(p.grossPaise()).isEqualTo(2500_00);
            assertThat(p.discountAmountPaise()).isEqualTo(250_00);
            assertThat(p.netPaise()).isEqualTo(2250_00);
            assertThat(p.gstPaise()).isEqualTo(405_00);
            assertThat(p.totalPaise()).isEqualTo(2655_00);
        }

        @Test
        @DisplayName("charging GST on the gross would be ₹45 more - guard against a regression to that order")
        void gstIsNotChargedOnGross() {
            var p = price("5", 500_00, GST_18, LineDiscount.Type.PERCENTAGE, "10", null);

            long gstOnGross = 450_00;
            assertThat(p.gstPaise()).isNotEqualTo(gstOnGross);
        }
    }

    @Nested
    @DisplayName("rejections")
    class Rejections {

        @Test
        @DisplayName("a negative percentage is refused, not clamped to zero")
        void negativePercentage() {
            assertThatThrownBy(() -> price("5", 100_00, NO_GST, LineDiscount.Type.PERCENTAGE, "-5", null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        @DisplayName("more than 100% is refused")
        void percentageOverHundred() {
            assertThatThrownBy(() -> price("5", 100_00, NO_GST, LineDiscount.Type.PERCENTAGE, "101", null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("100%");
        }

        @Test
        @DisplayName("a negative fixed amount is refused")
        void negativeAmount() {
            assertThatThrownBy(() -> price("5", 100_00, NO_GST, LineDiscount.Type.AMOUNT, null, -1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        @DisplayName("a fixed discount above the line gross is refused - this is the one that would go negative")
        void amountOverGross() {
            assertThatThrownBy(() -> price("5", 100_00, NO_GST, LineDiscount.Type.AMOUNT, null, 500_01L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("more than the line amount");
        }

        @Test
        @DisplayName("a fixed discount exactly equal to the gross is allowed")
        void amountEqualToGross() {
            var p = price("5", 100_00, NO_GST, LineDiscount.Type.AMOUNT, null, 500_00L);
            assertThat(p.netPaise()).isZero();
        }

        @Test
        @DisplayName("zero and negative quantities are refused")
        void badQuantity() {
            assertThatThrownBy(() -> price("0", 100_00, NO_GST, LineDiscount.Type.NONE, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Quantity");
            assertThatThrownBy(() -> price("-1", 100_00, NO_GST, LineDiscount.Type.NONE, null, null))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("the error names the product, so the owner knows which of ten lines is wrong")
        void errorNamesTheProduct() {
            assertThatThrownBy(() -> price("1", 100_00, NO_GST, LineDiscount.Type.AMOUNT, null, 999_00L))
                    .hasMessageContaining(LABEL);
        }
    }

    @Nested
    @DisplayName("nulls and historical rows")
    class Defaults {

        @Test
        @DisplayName("a null discount type prices exactly as an undiscounted line - this is how pre-CR-047 rows load")
        void nullTypeIsNone() {
            var p = LineDiscount.price(new BigDecimal("3"), 200_00, GST_18, null, null, null, LABEL);

            assertThat(p.discountAmountPaise()).isZero();
            assertThat(p.netPaise()).isEqualTo(600_00);
            assertThat(p.gstPaise()).isEqualTo(108_00);
        }

        @Test
        @DisplayName("a null percentage on a PERCENTAGE line is read as zero rather than throwing")
        void nullPercentage() {
            var p = price("2", 100_00, NO_GST, LineDiscount.Type.PERCENTAGE, null, null);
            assertThat(p.discountAmountPaise()).isZero();
        }

        @Test
        @DisplayName("a null GST rate is treated as zero-rated, not a crash")
        void nullGstRate() {
            var p = LineDiscount.price(new BigDecimal("2"), 100_00, null,
                    LineDiscount.Type.NONE, null, null, LABEL);
            assertThat(p.gstPaise()).isZero();
        }
    }

    @Nested
    @DisplayName("rounding")
    class Rounding {

        @Test
        @DisplayName("a third of a rupee rounds half-up to whole paise and never leaks a fraction")
        void roundsToWholePaise() {
            // 1 x ₹10.00 with 33.33% off = ₹3.333 -> 333 paise
            var p = price("1", 10_00, NO_GST, LineDiscount.Type.PERCENTAGE, "33.33", null);

            assertThat(p.discountAmountPaise()).isEqualTo(333);
            assertThat(p.netPaise()).isEqualTo(667);
        }

        @Test
        @DisplayName("fractional quantities price correctly - hardware is sold by the metre")
        void fractionalQuantity() {
            // 2.5 m x ₹100.00 = ₹250.00, 10% off = ₹25.00
            var p = price("2.5", 100_00, NO_GST, LineDiscount.Type.PERCENTAGE, "10", null);

            assertThat(p.grossPaise()).isEqualTo(250_00);
            assertThat(p.discountAmountPaise()).isEqualTo(25_00);
            assertThat(p.netPaise()).isEqualTo(225_00);
        }

        @Test
        @DisplayName("net + gst always equals total, whatever the rounding did")
        void totalIsAlwaysConsistent() {
            var p = price("7", 333_33, GST_18, LineDiscount.Type.PERCENTAGE, "7.5", null);
            assertThat(p.totalPaise()).isEqualTo(p.netPaise() + p.gstPaise());
            assertThat(p.netPaise()).isEqualTo(p.grossPaise() - p.discountAmountPaise());
        }
    }
}
