package com.hardware.erp.platformadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A reason is mandatory - every tenant suspension must be explainable later from the audit log alone. */
public record SuspendTenantRequest(
        @NotBlank @Size(max = 500) String reason
) {}
