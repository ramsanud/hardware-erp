package com.hardware.erp.customer.dto;

import com.hardware.erp.customer.entity.CustomerStatus;

public record CustomerSummaryResponse(
        Long id,
        String customerCode,
        String customerName,
        String mobileNo,
        String city,
        CustomerStatus status
) {}
