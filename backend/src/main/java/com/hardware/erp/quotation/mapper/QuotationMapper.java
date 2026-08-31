package com.hardware.erp.quotation.mapper;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.quotation.dto.QuotationItemResponse;
import com.hardware.erp.quotation.dto.QuotationResponse;
import com.hardware.erp.quotation.dto.QuotationSummaryResponse;
import com.hardware.erp.quotation.entity.Quotation;
import com.hardware.erp.quotation.entity.QuotationItem;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class QuotationMapper {

    public QuotationResponse toResponse(Quotation quotation) {
        return new QuotationResponse(
                quotation.getId(),
                quotation.getQuotationNumber(),
                quotation.getCustomer().getId(),
                quotation.getCustomer().getCustomerName(),
                quotation.getCustomer().getMobileNo(),
                quotation.getQuotationDate(),
                quotation.getValidUntil(),
                quotation.isExpired(),
                rupees(grossSubtotal(quotation)),
                nullIfZero(productDiscount(quotation)),
                rupees(grossSubtotal(quotation) - productDiscount(quotation)),
                quotation.getDiscountType(),
                quotation.getDiscountPercent().toPlainString(),
                nullIfZero(quotation.getDiscountPaise()),
                nullIfZero(productDiscount(quotation) + zeroIfNull(quotation.getDiscountPaise())),
                rupees(quotation.getSubtotalPaise()),
                rupees(quotation.getGstAmountPaise()),
                rupees(quotation.getTotalPaise()),
                quotation.getStatus(),
                quotation.getRemarks(),
                quotation.getConvertedInvoiceId(),
                quotation.getItems().stream()
                        .sorted(Comparator.comparing(QuotationItem::getId))
                        .map(this::toResponse)
                        .toList(),
                quotation.getCreatedAt());
    }

    public QuotationSummaryResponse toSummary(Quotation quotation) {
        return new QuotationSummaryResponse(
                quotation.getId(),
                quotation.getQuotationNumber(),
                quotation.getCustomer().getCustomerName(),
                quotation.getCustomer().getMobileNo(),
                quotation.getQuotationDate(),
                quotation.getValidUntil(),
                quotation.isExpired(),
                rupees(quotation.getTotalPaise()),
                quotation.getStatus());
    }

    public QuotationItemResponse toResponse(QuotationItem item) {
        return new QuotationItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProductNameSnapshot(),
                item.getQuantity(),
                rupees(item.getUnitPricePaise()),
                item.getGstRatePercent().toPlainString(),
                rupees(item.getLineSubtotalPaise()),
                rupees(item.getLineGstPaise()),
                rupees(item.getLineTotalPaise()),
                item.getDiscountType(),
                item.getDiscountPercent().toPlainString(),
                rupees(item.getDiscountAmountPaise()),
                rupees(lineGross(item)));
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    /** Sum of the per-line discounts (CR-047). Derived, never stored twice. */
    private static long productDiscount(Quotation quotation) {
        return quotation.getItems().stream()
                .mapToLong(item -> zeroIfNull(item.getDiscountAmountPaise()))
                .sum();
    }

    /**
     * subtotal_paise is NET of both discounts, so the original gross is
     * recovered by adding them back. Storing gross separately would be a
     * fourth figure that could disagree with the other three.
     */
    private static long grossSubtotal(Quotation quotation) {
        return quotation.getSubtotalPaise()
                + zeroIfNull(quotation.getDiscountPaise())
                + productDiscount(quotation);
    }

    /** Null rather than "0.00" so a quotation with no discount renders no discount rows. */
    private String nullIfZero(long paise) {
        return paise > 0 ? rupees(paise) : null;
    }

    /**
     * Line gross: quantity x unit price, computed from source (BUG-FE-017).
     *
     * It used to be reconstructed as lineSubtotalPaise + discountAmountPaise,
     * which is only right while the LINE discount is the sole thing that has
     * touched the subtotal. It is not: applyCoupon allocates a coupon across
     * lines by reducing lineSubtotalPaise, and CR-049 does the same with the
     * quotation-level discount. After either, the reconstruction returns the
     * discount alone - a 3 x ₹320 line showed a gross of ₹100.
     *
     * unit price and quantity are both snapshotted on the line and are never
     * rewritten, so they are the one honest source.
     */
    private static long lineGross(com.hardware.erp.quotation.entity.QuotationItem item) {
        return java.math.BigDecimal.valueOf(item.getUnitPricePaise())
                .multiply(item.getQuantity())
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact();
    }

    private String rupees(Long paise) {
        return IndianCurrencyFormat.rupees(paise);
    }
}
