package com.hardware.erp.purchase.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Supplier/Brand/Category are always resolved to a real id before this
 * reaches the server - the owner creates a new brand/category/supplier
 * through the existing Brand/Category/Supplier endpoints (already built,
 * already tested) the moment they click "Add Brand"/"Add Category"/
 * "Create new supplier" in the preview dialog, rather than this endpoint
 * reinventing that. Product is the one exception: existingProductId null
 * means "create this product," and that creation happens inside the same
 * transaction as the Purchase itself (spec §15 - a failed row must not
 * leave an orphaned product with no purchase behind it).
 */
public record ImportConfirmRow(
        int rowNumber,
        Long existingProductId,
        @Size(max = 255) String newProductName,
        /** The bill's own SKU/part number, if it had one - becomes the new product's code so a later re-order matches by code, not just name (spec §8's "existing/new" detection prefers an exact code match). Blank/null falls back to auto-generation, same as leaving Product's own code field empty anywhere else in the app. */
        @Size(max = 30) String newProductSku,
        Long newProductCategoryId,
        Long newProductBrandId,
        @Size(max = 20) String newProductUnit,
        @NotNull @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero")
        BigDecimal quantity,
        @NotNull @Min(value = 0, message = "Unit price cannot be negative")
        Long unitPricePaise,
        @NotNull @DecimalMin(value = "0", message = "GST rate cannot be negative")
        BigDecimal gstRatePercent,
        /** spec §12 - an existing product's own purchase price is never silently overwritten; the owner decides per row. Ignored when existingProductId is null (a brand-new product always takes this price as its first cost). */
        boolean updateExistingProductCost
) {}
