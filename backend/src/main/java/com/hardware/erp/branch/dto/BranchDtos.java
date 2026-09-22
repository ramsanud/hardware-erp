package com.hardware.erp.branch.dto;

import com.hardware.erp.branch.entity.BranchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** CR-092 multi-branch. */
public final class BranchDtos {

    private BranchDtos() {
    }

    @Schema(name = "BranchRequest")
    public record BranchRequest(
            @NotBlank @Size(max = 20)
            @Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{0,19}$", message = "Branch code may contain uppercase letters, digits and hyphens")
            String branchCode,
            @NotBlank @Size(max = 150) String branchName,
            @Size(max = 255) String addressLine1,
            @Size(max = 100) String city,
            @Pattern(regexp = "^$|^[0-9]{2}$", message = "State code is the 2-digit GST state code") String stateCode,
            @Pattern(regexp = "^$|^[1-9][0-9]{5}$", message = "Enter a valid 6-digit PIN code") String pincode,
            @Pattern(regexp = "^$|^[6-9][0-9]{9}$", message = "Enter a valid 10-digit mobile number") String phone,
            BranchStatus status
    ) {}

    @Schema(name = "BranchResponse")
    public record BranchResponse(
            Long id,
            String branchCode,
            String branchName,
            String addressLine1,
            String city,
            String stateCode,
            String pincode,
            String phone,
            boolean main,
            BranchStatus status,
            LocalDateTime createdAt
    ) {}

    @Schema(name = "BranchSummaryResponse", description = "Branch-wise figures for a date range - every number a recorded row.")
    public record BranchSummaryResponse(
            Long branchId,
            String branchCode,
            String branchName,
            boolean main,
            long invoiceCount,
            long salesPaise,
            String salesDisplay,
            long purchaseCount,
            long purchasesPaise,
            String purchasesDisplay,
            long userCount,
            long productsInStock
    ) {}

    @Schema(name = "BranchStockResponse")
    public record BranchStockResponse(
            Long branchId,
            String branchName,
            Long productId,
            String productCode,
            String productName,
            String unit,
            BigDecimal quantityOnHand
    ) {}

    @Schema(name = "AssignUserBranchRequest")
    public record AssignUserBranchRequest(
            @Schema(description = "Null clears the assignment - the user then works across every branch.") Long branchId
    ) {}

    @Schema(name = "StockTransferItemRequest")
    public record StockTransferItemRequest(
            @NotNull Long productId,
            @NotNull @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero") BigDecimal quantity
    ) {}

    @Schema(name = "StockTransferRequest")
    public record StockTransferRequest(
            @NotNull Long fromBranchId,
            @NotNull Long toBranchId,
            @NotEmpty @Valid List<StockTransferItemRequest> items,
            @Size(max = 500) String notes
    ) {}

    @Schema(name = "StockTransferItemResponse")
    public record StockTransferItemResponse(Long productId, String productName, BigDecimal quantity) {}

    @Schema(name = "StockTransferResponse")
    public record StockTransferResponse(
            Long id,
            String transferNumber,
            Long fromBranchId,
            String fromBranchName,
            Long toBranchId,
            String toBranchName,
            String status,
            String notes,
            List<StockTransferItemResponse> items,
            LocalDateTime createdAt
    ) {}
}
