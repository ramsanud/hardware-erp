package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.Set;

@Schema(name = "RoleRequest")
public record RoleRequest(

        @Schema(example = "STOCK_CLERK")
        @NotBlank(message = "Role code is required")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$",
                 message = "Role code must be uppercase letters, digits and underscores")
        String code,

        @Schema(example = "Stock Clerk")
        @NotBlank(message = "Role name is required")
        @Size(max = 100, message = "Role name must be 100 characters or fewer")
        String name,

        @Schema(example = "Godown staff: receives stock, cannot bill")
        @Size(max = 255, message = "Description must be 255 characters or fewer")
        String description,

        @Schema(example = "[\"PRODUCT_VIEW\",\"INVENTORY_VIEW\",\"INVENTORY_ADJUST\"]")
        @NotEmpty(message = "Select at least one permission")
        Set<String> permissions,

        @Schema(example = "ACTIVE")
        @NotNull(message = "Status is required")
        com.hardware.erp.auth.entity.RoleStatus status
) {}
