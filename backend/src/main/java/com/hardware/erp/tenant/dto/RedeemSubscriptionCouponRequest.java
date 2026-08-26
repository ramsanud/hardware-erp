package com.hardware.erp.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record RedeemSubscriptionCouponRequest(
        @NotBlank String code
) {}
