package com.hardware.erp.deliverychallan.dto;

import com.hardware.erp.deliverychallan.entity.DeliveryChallanStatus;

import java.time.LocalDate;

public record DeliveryChallanSummaryResponse(
        Long id,
        String deliveryChallanNumber,
        String customerName,
        String customerMobile,
        LocalDate challanDate,
        String totalValueDisplay,
        DeliveryChallanStatus status
) {}
