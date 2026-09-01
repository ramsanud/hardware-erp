package com.hardware.erp.salesorder.dto;

import com.hardware.erp.salesorder.entity.SalesOrderStatus;

import com.hardware.erp.common.util.LineDiscount;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SalesOrderResponse(
        Long id,
        String salesOrderNumber,
        Long customerId,
        String customerName,
        String customerMobile,
        LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        /** Same full ladder as QuotationResponse - see its header comment. */
        String grossSubtotalDisplay,
        String productDiscountDisplay,
        String afterProductDiscountDisplay,
        LineDiscount.Type orderDiscountType,
        String orderDiscountPercent,
        String orderDiscountDisplay,
        String totalSavingsDisplay,
        String subtotalDisplay,
        String gstAmountDisplay,
        String totalDisplay,
        SalesOrderStatus status,
        String remarks,
        Long convertedDeliveryChallanId,
        Long convertedInvoiceId,
        List<SalesOrderItemResponse> items,
        LocalDateTime createdAt
) {}
