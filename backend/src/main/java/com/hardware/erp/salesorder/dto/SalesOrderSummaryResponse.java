package com.hardware.erp.salesorder.dto;

import com.hardware.erp.salesorder.entity.SalesOrderStatus;

import java.time.LocalDate;

public record SalesOrderSummaryResponse(
        Long id,
        String salesOrderNumber,
        String customerName,
        String customerMobile,
        LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        String totalDisplay,
        SalesOrderStatus status
) {}
