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
                rupees(item.getLineTotalPaise()));
    }

    private String rupees(Long paise) {
        return IndianCurrencyFormat.rupees(paise);
    }
}
