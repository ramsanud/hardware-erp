package com.hardware.erp.labour.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record WorkerRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 15) String mobileNo,
        @Size(max = 100) String roleTitle,
        @NotNull @Positive Long dailyRatePaise
) {}
