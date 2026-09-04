package com.hardware.erp.platformadmin.dto;

import com.hardware.erp.platformadmin.entity.FeatureFlagScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFeatureFlagRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9_.-]+$", message = "Use lowercase letters, numbers, '_', '.' or '-' only")
        String flagKey,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description,
        @NotNull FeatureFlagScope scope
) {}
