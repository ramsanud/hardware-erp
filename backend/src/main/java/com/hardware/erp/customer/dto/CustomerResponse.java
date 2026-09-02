package com.hardware.erp.customer.dto;

import com.hardware.erp.customer.entity.CustomerStatus;

import java.time.LocalDateTime;

public record CustomerResponse(
        Long id,
        String customerCode,
        String customerName,
        String mobileNo,
        String email,
        String gstNo,
        String addressLine1,
        String addressLine2,
        String city,
        String stateCode,
        String pincode,
        String creditLimitDisplay,
        CustomerStatus status,
        boolean whatsappOptIn,
        LocalDateTime createdAt
) {}
