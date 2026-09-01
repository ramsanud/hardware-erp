package com.hardware.erp.deliverychallan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record DeliveryChallanRequest(
        @NotBlank @Size(max = 255) String customerName,
        @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a valid 10-digit mobile number")
        String customerMobile,
        @Email @Size(max = 255) String customerEmail,
        @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
                message = "Enter a valid 15-character GSTIN")
        String customerGstNo,
        @Pattern(regexp = "^$|^[0-9]{2}$", message = "State code is the 2-digit GST state code")
        String customerStateCode,
        @NotEmpty @Valid List<DeliveryChallanItemRequest> items,
        @Size(max = 50) String transportMode,
        @Size(max = 20) String vehicleNumber,
        @Size(max = 500) String deliveryAddress,
        @Size(max = 500) String remarks
) {}
