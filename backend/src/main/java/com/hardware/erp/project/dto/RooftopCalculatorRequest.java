package com.hardware.erp.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(name = "RooftopCalculatorRequest",
        description = "Required area + overlap% + wastage%, divided by one sheet's area, rounded up - a starting estimate, always editable by hand afterward.")
public record RooftopCalculatorRequest(
        @NotNull @DecimalMin(value = "0.01", message = "Width must be greater than zero")
        BigDecimal widthMeters,

        @NotNull @DecimalMin(value = "0.01", message = "Length must be greater than zero")
        BigDecimal lengthMeters,

        @NotNull @DecimalMin(value = "0.01", message = "Sheet width must be greater than zero")
        BigDecimal sheetWidthMeters,

        @NotNull @DecimalMin(value = "0.01", message = "Sheet length must be greater than zero")
        BigDecimal sheetLengthMeters,

        @Schema(description = "Overlap between adjacent sheets, as a percentage of area", example = "10")
        @DecimalMin(value = "0", message = "Overlap cannot be negative")
        BigDecimal overlapPercent,

        @Schema(description = "Extra allowance for cutting/handling/damage, as a percentage of area", example = "5")
        @DecimalMin(value = "0", message = "Wastage cannot be negative")
        BigDecimal wastagePercent
) {}
