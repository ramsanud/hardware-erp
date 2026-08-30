package com.hardware.erp.common.util;

import com.hardware.erp.common.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single authority for how one quotation or invoice line is priced
 * (CR-047).
 *
 * Before this class the same arithmetic was written out twice - once in
 * InvoiceServiceImpl.buildLine and once in QuotationServiceImpl.buildLine -
 * which is exactly the shape that lets a quotation and the invoice it
 * converts into disagree by a rupee. Both now call {@link #price}.
 *
 * Money is BIGINT paise throughout, never double: the project's rule. The one
 * BigDecimal step is the percentage and GST arithmetic, which is done at
 * BigDecimal precision and rounded to whole paise exactly once, HALF_UP -
 * matching the rounding the coupon path and the pre-existing line maths
 * already used, so no existing total shifts.
 *
 * Order of operations is fixed and deliberate:
 *
 *     gross     = quantity x unit price
 *     discount  = percentage of gross, or the entered amount
 *     net       = gross - discount          <- this is line_subtotal_paise
 *     gst       = net x gst rate            <- GST is charged on the
 *     total     = net + gst                    DISCOUNTED value, not gross
 *
 * A coupon, if one is applied afterwards, reduces the same net figure again
 * through InvoiceServiceImpl.applyCoupon. Manual discount first, coupon
 * second; neither is counted twice because the coupon reads the already-net
 * line_subtotal_paise rather than recomputing from the unit price.
 */
public final class LineDiscount {

    private LineDiscount() {
    }

    /** Mirrors the discount_type CHECK constraint in V31. */
    public enum Type {
        NONE, PERCENTAGE, AMOUNT
    }

    /**
     * @param discountAmountPaise the authoritative money figure for both
     *                            discount types - what gets stored and what
     *                            every total is built from
     * @param netPaise            the taxable amount, stored as line_subtotal_paise
     */
    public record Priced(
            long grossPaise,
            long discountAmountPaise,
            long netPaise,
            long gstPaise,
            long totalPaise
    ) {}

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Validates and prices one line. Throws {@link BusinessException} rather
     * than returning a flag: an invalid discount must never be silently
     * clamped into a valid one, because the owner would then see a total they
     * did not agree to.
     *
     * @param productLabel used only in error messages, so the owner is told
     *                     which of ten lines is wrong
     */
    public static Priced price(BigDecimal quantity,
                               long unitPricePaise,
                               BigDecimal gstRatePercent,
                               Type discountType,
                               BigDecimal discountPercent,
                               Long discountAmountPaise,
                               String productLabel) {

        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("Quantity for '" + productLabel + "' must be greater than zero");
        }
        if (unitPricePaise < 0) {
            throw new BusinessException("Unit price for '" + productLabel + "' cannot be negative");
        }

        long gross = BigDecimal.valueOf(unitPricePaise)
                .multiply(quantity)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        Type type = discountType == null ? Type.NONE : discountType;
        long discount = switch (type) {
            case NONE -> 0L;
            case PERCENTAGE -> percentageDiscount(gross, discountPercent, productLabel);
            case AMOUNT -> fixedDiscount(discountAmountPaise, productLabel);
        };

        // The cap is checked for both types. A percentage can only reach the
        // gross at exactly 100%, but an AMOUNT is free-typed and is the case
        // that would otherwise drive the line negative.
        if (discount > gross) {
            throw new BusinessException(
                    "Discount on '" + productLabel + "' cannot be more than the line amount");
        }

        long net = gross - discount;
        long gst = BigDecimal.valueOf(net)
                .multiply(gstRatePercent == null ? BigDecimal.ZERO : gstRatePercent)
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .longValueExact();

        return new Priced(gross, discount, net, gst, net + gst);
    }

    private static long percentageDiscount(long gross, BigDecimal percent, String productLabel) {
        if (percent == null) {
            return 0L;
        }
        if (percent.signum() < 0) {
            throw new BusinessException("Discount on '" + productLabel + "' cannot be negative");
        }
        if (percent.compareTo(HUNDRED) > 0) {
            throw new BusinessException("Discount on '" + productLabel + "' cannot be more than 100%");
        }
        return BigDecimal.valueOf(gross)
                .multiply(percent)
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static long fixedDiscount(Long amountPaise, String productLabel) {
        if (amountPaise == null) {
            return 0L;
        }
        if (amountPaise < 0) {
            throw new BusinessException("Discount on '" + productLabel + "' cannot be negative");
        }
        return amountPaise;
    }
}
