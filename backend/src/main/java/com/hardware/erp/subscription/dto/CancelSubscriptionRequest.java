package com.hardware.erp.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CancelSubscriptionRequest")
public record CancelSubscriptionRequest(
        @NotBlank(message = "Please tell us why you are cancelling")
        @Size(max = 255) String reason
) {}
