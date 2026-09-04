package com.hardware.erp.product.dto;

import com.hardware.erp.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(name = "ProductRequest", description = "Create or update a product")
public record ProductRequest(

        @Schema(description = "Leave blank on create and the system generates PRD-000001, PRD-000002 ...",
                example = "PRD-000042")
        @Size(max = 30, message = "Product code must be 30 characters or fewer")
        @Pattern(regexp = "^$|^[A-Z0-9][A-Z0-9-]{1,29}$",
                 message = "Product code may contain uppercase letters, digits and hyphens")
        String productCode,

        @Schema(example = "Godrej Ultra Premium Lock 60mm")
        @NotBlank(message = "Product name is required")
        @Size(max = 255) String productName,

        @Schema(example = "3") Long categoryId,

        @Schema(example = "4") Long brandId,

        @Schema(example = "UL-60-BR") @Size(max = 60) String modelNo,

        @Schema(example = "GDJ-LK-6001") @Size(max = 60) String manufacturerCode,

        @Schema(example = "8901030812345") @Size(max = 60) String barcode,

        @Schema(example = "PCS")
        @NotBlank(message = "Unit is required")
        @Size(max = 20) String unit,

        @Schema(example = "5-lever brass lock body, brown finish") String description,

        @Schema(description = "HSN/SAC code", example = "8301") @Size(max = 10) String hsnCode,

        @Schema(example = "18.00")
        @NotNull(message = "GST rate is required")
        @DecimalMin(value = "0", message = "GST rate cannot be negative")
        @DecimalMax(value = "100", message = "GST rate cannot exceed 100")
        BigDecimal gstRatePercent,

        @Schema(description = "Purchase price in paise. Only visible to PRODUCT_VIEW_COST.",
                example = "45000")
        @NotNull(message = "Purchase price is required")
        @Min(value = 0, message = "Purchase price cannot be negative")
        Long purchasePricePaise,

        @Schema(description = "Selling price in paise", example = "65000")
        @NotNull(message = "Selling price is required")
        @Min(value = 0, message = "Selling price cannot be negative")
        Long sellingPricePaise,

        @Schema(description = "MRP in paise. 0 means not applicable.", example = "75000")
        @NotNull(message = "MRP is required")
        @Min(value = 0, message = "MRP cannot be negative")
        Long mrpPaise,

        @Schema(example = "5")
        @NotNull(message = "Minimum stock is required")
        @DecimalMin(value = "0", message = "Minimum stock cannot be negative")
        BigDecimal minimumStock,

        @Schema(example = "10")
        @NotNull(message = "Reorder level is required")
        @DecimalMin(value = "0", message = "Reorder level cannot be negative")
        BigDecimal reorderLevel,

        @Schema(example = "ACTIVE")
        @NotNull(message = "Status is required")
        ProductStatus status,

        @Schema(description = "CR-053 backlog item 1. Secondary unit label, e.g. \"BOX\" - null if this product has no alternate unit.",
                example = "BOX")
        @Size(max = 30) String altUnitLabel,

        @Schema(description = "How many of `unit` make one `altUnitLabel`, e.g. 12 for \"1 BOX = 12 PCS\". Must be set together with altUnitLabel or not at all.",
                example = "12")
        @DecimalMin(value = "0.0001", message = "Alternate unit conversion factor must be greater than zero")
        BigDecimal altUnitConversionFactor
) {}
