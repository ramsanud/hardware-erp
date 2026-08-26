package com.hardware.erp.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactAdminRequest(
        @NotBlank @Size(max = 150) String subject,
        @NotBlank @Size(max = 2000) String message
) {}
