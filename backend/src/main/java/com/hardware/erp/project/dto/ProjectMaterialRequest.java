package com.hardware.erp.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "ProjectMaterialRequest", description = "Add or update a material line on a project. unitPricePaise defaults to the product's current selling price if omitted.")
public record ProjectMaterialRequest(
        @Schema(description = "An existing product id - use the product create endpoint first if it doesn't exist, then pass the new id here", example = "42")
        @NotNull(message = "Product is required")
        Long productId,

        @Schema(description = "Optional - some old stock was never recorded with a supplier", example = "3")
        Long supplierId,

        BigDecimal quantityRequired,
        BigDecimal quantityEstimated,
        BigDecimal quantityActual,

        @Schema(description = "Extra quantity lost to cutting, handling or damage")
        BigDecimal quantityWastage,

        @Schema(description = "Paise per unit - omit to use the product's current selling price")
        @Min(value = 0, message = "Unit price cannot be negative")
        Long unitPricePaise,

        @Size(max = 500)
        String notes
) {}
