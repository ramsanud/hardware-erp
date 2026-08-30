package com.hardware.erp.common.util;

import com.hardware.erp.common.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single authority for how one quotation or invoice line is priced
 * (CR-047, CR-049, CR-050).
 *
 * Both InvoiceServiceImpl.buildLine and QuotationServiceImpl.buildLine call
 * {@link #price}. Before it existed the arithmetic was written out twice,
 * which is exactly the shape that lets a quotation and the invoice it
 * converts into disagree by a rupee.
 *
 * ORDER OF OPERATIONS (CR-050, agreed with the owner):
 *
 *     gross          quantity x unit price
 *     - discount     a PERCENTAGE of gross
 *     = afterDiscount
 *     + labour       a percentage of the DISCOUNTED value, not of gross
 *     = net          <- line_subtotal_paise, the taxable amount
 *     + GST          charged on net
 *     = total
 *
 * DISCOUNT IS PERCENTAGE-ONLY (CR-050). The fixed-amount option that
 * CR-047 shipped was retired; V33 converted every stored AMOUNT row to the
 * equivalent percentage without moving any money.
 *
 * THE DISCOUNT BASE IS THE SELLING PRICE, not MRP. Every product in the
 * catalogue has an MRP above its selling price, so discounting from MRP
 * would re-price the whole catalogue upward. MRP is carried on the line for
 * display and comparison only.
 *
 * LABOUR IS INTERNAL. It is an owner-side margin folded into the rate, never
 * a separate line on a customer document. The rate the customer sees is the
 * rate actually charged - see PricedLine.effectiveUnitPricePaise - so the
 * document stays arithmetically honest even though the split is not shown.
 *
 * Money is BIGINT paise throughout, never double. Percentage arithmetic is
 * done at BigDecimal precision and rounded to whole paise exactly once,
 * HALF_UP, matching the rounding the rest of the codebase already uses.
 */
public final class LineDiscount {

    private LineDiscount() {
    }

    /**
     * Mirrors the discount_type CHECK constraint. AMOUNT was removed in V33;
     * the constant is gone so a fixed-amount discount cannot be reintroduced
     * by accident.
     */
    public enum Type {
        NONE, PERCENTAGE
    }

    /**
     * @param discountAmountPaise      money off, computed from the percentage
     * @param labourAmountPaise        internal margin added back on; owner-only
     * @param netPaise                 the taxable amount, stored as line_subtotal_paise
     * @param effectiveUnitPricePaise  net / quantity - the rate the customer is
     *                                 actually charged, and the only rate a
     *                                 customer-facing document may print
     */
    public record Priced(
            long grossPaise,
            long discountAmountPaise,
            long afterDiscountPaise,
            long labourAmountPaise,
            long netPaise,
            long effectiveUnitPricePaise,
            long gstPaise,
            long totalPaise
    ) {}

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Validates and prices one line. Throws {@link BusinessException} rather
     * than clamping: an invalid discount must never be silently turned into a
     * valid one, because the owner would then see a total they did not agree
     * to.
     *
     * @param productLabel used only in error messages, so the owner is told
     *                     which of ten lines is wrong
     */
    public static Priced price(BigDecimal quantity,
                               long unitPricePaise,
                               BigDecimal gstRatePercent,
                               Type discountType,
                               BigDecimal discountPercent,
                               BigDecimal labourPercent,
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
        long discount = type == Type.PERCENTAGE
                ? percentageOf(gross, discountPercent, productLabel, "Discount")
                : 0L;

        // A percentage can only reach the gross at exactly 100%, so this can
        // never go negative - but it is asserted rather than assumed, because
        // a future change to the base is exactly how that would break.
        long afterDiscount = gross - discount;
        if (afterDiscount < 0) {
            throw new BusinessException(
                    "Discount on '" + productLabel + "' cannot be more than the line amount");
        }

        // Labour is taken off the DISCOUNTED value, not the gross. Taking it
        // off the gross would quietly hand back part of the discount.
        long labour = percentageOf(afterDiscount, labourPercent, productLabel, "Labour");

        long net = afterDiscount + labour;

        // The rate the customer is charged. Derived from the net rather than
        // stored, so it can never disagree with the line total.
        long effectiveUnitPrice = BigDecimal.valueOf(net)
                .divide(quantity, 0, RoundingMode.HALF_UP)
                .longValueExact();

        long gst = BigDecimal.valueOf(net)
                .multiply(gstRatePercent == null ? BigDecimal.ZERO : gstRatePercent)
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .longValueExact();

        return new Priced(gross, discount, afterDiscount, labour, net,
                effectiveUnitPrice, gst, net + gst);
    }

    private static long percentageOf(long base, BigDecimal percent, String productLabel, String what) {
        if (percent == null || percent.signum() == 0) {
            return 0L;
        }
        if (percent.signum() < 0) {
            throw new BusinessException(what + " on '" + productLabel + "' cannot be negative");
        }
        if (percent.compareTo(HUNDRED) > 0) {
            throw new BusinessException(what + " on '" + productLabel + "' cannot be more than 100%");
        }
        return BigDecimal.valueOf(base)
                .multiply(percent)
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
