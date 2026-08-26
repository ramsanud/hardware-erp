package com.hardware.erp.supplier.dto;

import com.hardware.erp.supplier.entity.SupplierStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(name = "SupplierRequest", description = "Create or update a supplier")
public record SupplierRequest(

        @Schema(description = "Leave blank on create and the system generates SUP-0001, SUP-0002 ...",
                example = "SUP-0007")
        @Size(max = 30, message = "Supplier code must be 30 characters or fewer")
        @Pattern(regexp = "^$|^[A-Z0-9][A-Z0-9-]{1,29}$",
                 message = "Supplier code may contain uppercase letters, digits and hyphens")
        String supplierCode,

        @Schema(example = "Sri Balaji Hardware Agencies")
        @NotBlank(message = "Supplier name is required")
        @Size(max = 255, message = "Supplier name must be 255 characters or fewer")
        String supplierName,

        @Schema(example = "Ramesh Kumar")
        @Size(max = 200)
        String contactPerson,

        @Schema(example = "9842011223")
        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = "^[6-9]\\d{9}$",
                 message = "Enter a valid 10-digit Indian mobile number")
        String mobileNo,

        @Schema(example = "9842011224")
        @Pattern(regexp = "^$|^[6-9]\\d{9}$",
                 message = "Enter a valid 10-digit Indian mobile number")
        String alternateMobileNo,

        @Schema(example = "sales@sribalajihardware.in")
        @Email(message = "Enter a valid email address")
        @Size(max = 255)
        String email,

        @Schema(description = "15-character GSTIN", example = "33AABCS1429B1ZP")
        @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]$",
                 message = "Enter a valid 15-character GSTIN")
        String gstNo,

        @Schema(example = "AABCS1429B")
        @Pattern(regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]$",
                 message = "Enter a valid 10-character PAN")
        String panNo,

        @Schema(example = "144 Big Bazaar Street")
        @Size(max = 255) String addressLine1,

        @Schema(example = "Near Central Market")
        @Size(max = 255) String addressLine2,

        @Schema(example = "Madurai")
        @Size(max = 100) String city,

        @Schema(description = "GST state code. 33 = Tamil Nadu", example = "33")
        @Pattern(regexp = "^$|^[0-9]{2}$", message = "State code must be two digits")
        String stateCode,

        @Schema(example = "625001")
        @Pattern(regexp = "^$|^[1-9][0-9]{5}$", message = "Enter a valid 6-digit pincode")
        String pincode,

        @Schema(description = "Credit period in days. 0 means cash on delivery.", example = "30")
        @NotNull(message = "Payment terms are required")
        @Min(value = 0, message = "Payment terms cannot be negative")
        @Max(value = 365, message = "Payment terms cannot exceed 365 days")
        Integer paymentTermsDays,

        @Schema(description = "Credit ceiling in paise. 50000000 = Rs 5,00,000.",
                example = "50000000")
        @NotNull(message = "Credit limit is required")
        @Min(value = 0, message = "Credit limit cannot be negative")
        Long creditLimitPaise,

        @Schema(example = "Sri Balaji Hardware Agencies")
        @Size(max = 200) String bankAccountName,

        @Schema(example = "50100234567890")
        @Size(max = 30) String bankAccountNo,

        @Schema(example = "HDFC0001234")
        @Pattern(regexp = "^$|^[A-Z]{4}0[A-Z0-9]{6}$", message = "Enter a valid IFSC code")
        String bankIfsc,

        @Schema(example = "HDFC Bank")
        @Size(max = 200) String bankName,

        @Schema(example = "ACTIVE")
        @NotNull(message = "Status is required")
        SupplierStatus status,

        @Schema(example = "Reliable for locks and door closers. Slow on mica sheets.")
        String remarks
) {}
