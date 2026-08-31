package com.hardware.erp.quotation.dto;

import com.hardware.erp.quotation.entity.QuotationStatus;

import com.hardware.erp.common.util.LineDiscount;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record QuotationResponse(
        Long id,
        String quotationNumber,
        Long customerId,
        String customerName,
        String customerMobile,
        LocalDate quotationDate,
        LocalDate validUntil,
        boolean expired,
        /**
         * CR-049. The full ladder, so the customer-facing quotation can show
         * what was charged before discount, what came off, and why - without
         * the reader having to do the arithmetic themselves.
         *
         * grossSubtotalDisplay   qty x price, before any discount
         * productDiscountDisplay sum of the per-line discounts (CR-047)
         * afterProductDiscountDisplay  the base the quotation discount applies to
         * quotationDiscountDisplay     the whole-quotation discount (CR-049)
         * subtotalDisplay        TAXABLE amount, net of both
         * totalSavingsDisplay    productDiscount + quotationDiscount
         *
         * The three discount fields are null when zero, so a quotation with no
         * discount renders no discount rows at all.
         */
        String grossSubtotalDisplay,
        String productDiscountDisplay,
        String afterProductDiscountDisplay,
        LineDiscount.Type quotationDiscountType,
        String quotationDiscountPercent,
        String quotationDiscountDisplay,
        String totalSavingsDisplay,
        String subtotalDisplay,
        String gstAmountDisplay,
        String totalDisplay,
        QuotationStatus status,
        String remarks,
        Long convertedInvoiceId,
        List<QuotationItemResponse> items,
        LocalDateTime createdAt
) {}
