package com.hardware.erp.deliverychallan.dto;

import com.hardware.erp.deliverychallan.entity.DeliveryChallanStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DeliveryChallanResponse(
        Long id,
        String deliveryChallanNumber,
        Long customerId,
        String customerName,
        String customerMobile,
        LocalDate challanDate,
        String transportMode,
        String vehicleNumber,
        String deliveryAddress,
        String totalValueDisplay,
        DeliveryChallanStatus status,
        String remarks,
        Long sourceSalesOrderId,
        Long convertedInvoiceId,
        List<DeliveryChallanItemResponse> items,
        LocalDateTime createdAt
) {}
