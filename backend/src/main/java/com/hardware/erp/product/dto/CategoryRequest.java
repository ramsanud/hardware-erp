package com.hardware.erp.product.dto;

import com.hardware.erp.product.entity.CategoryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(name = "CategoryRequest", description = "Create or update a category")
public record CategoryRequest(

        @Schema(description = "Leave blank on create and the system generates CAT-0001, CAT-0002 ...",
                example = "CAT-0003")
        @Size(max = 30, message = "Category code must be 30 characters or fewer")
        @Pattern(regexp = "^$|^[A-Z0-9][A-Z0-9-]{1,29}$",
                 message = "Category code may contain uppercase letters, digits and hyphens")
        String categoryCode,

        @Schema(example = "Hand Tools")
        @NotBlank(message = "Category name is required")
        @Size(max = 150, message = "Category name must be 150 characters or fewer")
        String categoryName,

        @Schema(description = "Leave blank for a top-level category", example = "1")
        Long parentCategoryId,

        @Schema(example = "Hammers, wrenches, screwdrivers and similar hand-operated tools")
        @Size(max = 255) String description,

        @Schema(example = "ACTIVE")
        @NotNull(message = "Status is required")
        CategoryStatus status
) {}
