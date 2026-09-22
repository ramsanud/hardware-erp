package com.hardware.erp.product.substitute.dto;

import com.hardware.erp.product.substitute.entity.MatchLevel;
import com.hardware.erp.product.substitute.entity.ProductRequestStatus;
import com.hardware.erp.product.substitute.entity.RelationshipType;
import com.hardware.erp.product.substitute.entity.SuggestionSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CR-089. Every DTO for the Smart Substitute feature in one file, the way
 * report/dto/ReportDtos.java already groups its own - these are small
 * records read together, and splitting them across fourteen files would
 * make the contract harder to see, not easier.
 *
 * Note what is NOT here: no purchase price, no margin, no supplier. Counter
 * staff (no PRODUCT_VIEW_COST) use this screen, and §2 is explicit that
 * internal business information must not surface through it.
 */
public final class SubstituteDtos {

    private SubstituteDtos() {
    }

    @Schema(name = "CreateProductRequestRequest", description = "Record what a customer asked for that could not be sold now")
    public record CreateProductRequest(
            @Schema(example = "42")
            @NotNull(message = "The requested product is required")
            Long productId,

            @Schema(example = "10")
            @NotNull(message = "Requested quantity is required")
            @DecimalMin(value = "0.0001", message = "Requested quantity must be greater than zero")
            BigDecimal requestedQuantity,

            @Schema(description = "Optional. What the customer said they would spend, in paise.", example = "20000")
            @Min(value = 0, message = "Budget cannot be negative")
            Long requestedBudgetPaise,

            @Schema(example = "Ramesh") @Size(max = 200) String customerName,

            @Schema(example = "9876500001")
            @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Enter a valid 10-digit mobile number")
            @Size(max = 15) String customerMobile
    ) {}

    @Schema(name = "SelectAlternativeRequest")
    public record SelectAlternativeRequest(
            @Schema(description = "The product the owner chose to offer. Must be one of the suggestions on this request.", example = "57")
            @NotNull(message = "Select one of the suggested alternatives")
            Long productId
    ) {}

    @Schema(name = "ProductRelationshipRequest")
    public record CreateRelationshipRequest(
            @Schema(example = "57")
            @NotNull(message = "The related product is required")
            Long relatedProductId,

            @Schema(example = "ALTERNATIVE")
            @NotNull(message = "Relationship type is required")
            RelationshipType relationshipType,

            @Schema(example = "Same fitting, stainless body") @Size(max = 255) String notes
    ) {}

    @Schema(name = "SubstituteSettingRequest")
    public record SubstituteSettingRequest(
            @Schema(description = "Suggestions below this score are never shown. 0-110.", example = "40")
            @NotNull @Min(0) Integer minScoreThreshold,

            @Schema(description = "Whether to show alternatives priced above the customer's stated budget.", example = "true")
            @NotNull Boolean showAboveBudget,

            @Schema(description = "How many alternatives to show by default.", example = "3")
            @NotNull @Min(1) Integer maxResults
    ) {}

    @Schema(name = "SubstituteSettingResponse")
    public record SubstituteSettingResponse(
            int minScoreThreshold,
            boolean showAboveBudget,
            int maxResults
    ) {}

    /** One product as it appears in a suggestion or comparison. Selling price only - never cost. */
    @Schema(name = "SubstituteProductResponse")
    public record SubstituteProductResponse(
            Long id,
            String productCode,
            String productName,
            String categoryName,
            String brandName,
            String productType,
            String usageType,
            String sizeLabel,
            String material,
            String colorFinish,
            String shape,
            String unit,
            long sellingPricePaise,
            String sellingPriceDisplay,
            @Schema(description = "Quantity on hand right now.") BigDecimal availableStock,
            boolean inStock
    ) {}

    @Schema(name = "SubstituteSuggestionResponse")
    public record SuggestionResponse(
            SubstituteProductResponse product,
            int score,
            @Schema(description = "Maximum score achievable, so the UI can show score/max honestly.") int maximumScore,
            MatchLevel matchLevel,
            String reason,
            SuggestionSource source,
            @Schema(description = "True when the product costs more than the customer's stated budget.") boolean aboveBudget
    ) {}

    @Schema(name = "ProductRequestResponse")
    public record ProductRequestResponse(
            Long id,
            SubstituteProductResponse requestedProduct,
            BigDecimal requestedQuantity,
            Long requestedBudgetPaise,
            String requestedBudgetDisplay,
            String customerName,
            String customerMobile,
            ProductRequestStatus status,
            @Schema(description = "Stock of the requested product at the moment this response was built.") BigDecimal requestedProductStock,
            SubstituteProductResponse selectedProduct,
            LocalDateTime selectedAt,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt,
            List<SuggestionResponse> suggestions
    ) {}

    @Schema(name = "ProductRelationshipResponse")
    public record RelationshipResponse(
            Long id,
            SubstituteProductResponse relatedProduct,
            RelationshipType relationshipType,
            String notes,
            LocalDateTime createdAt
    ) {}

    /** §12's compare view - the requested product and one alternative, attribute by attribute, built server-side so the screen invents nothing. */
    @Schema(name = "SubstituteComparisonResponse")
    public record ComparisonResponse(
            SubstituteProductResponse requested,
            SubstituteProductResponse alternative,
            List<ComparisonRow> rows
    ) {}

    @Schema(name = "SubstituteComparisonRow")
    public record ComparisonRow(
            String label,
            String requestedValue,
            String alternativeValue,
            boolean same
    ) {}
}
