package com.hardware.erp.product.dto;

import com.hardware.erp.product.entity.BrandStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(name = "BrandRequest", description = "Create or update a brand")
public record BrandRequest(

        @Schema(description = "Leave blank on create and the system generates BRD-0001, BRD-0002 ...",
                example = "BRD-0004")
        @Size(max = 30, message = "Brand code must be 30 characters or fewer")
        @Pattern(regexp = "^$|^[A-Z0-9][A-Z0-9-]{1,29}$",
                 message = "Brand code may contain uppercase letters, digits and hyphens")
        String brandCode,

        @Schema(example = "Godrej")
        @NotBlank(message = "Brand name is required")
        @Size(max = 150, message = "Brand name must be 150 characters or fewer")
        String brandName,

        @Schema(example = "Locks, furniture fittings and security hardware")
        @Size(max = 255) String description,

        @Schema(example = "ACTIVE")
        @NotNull(message = "Status is required")
        BrandStatus status
) {}
