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
                rupees(item.getLineSubtotalPaise() + item.getDiscountAmountPaise()));
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

    private String rupees(Long paise) {
        return IndianCurrencyFormat.rupees(paise);
    }
}
