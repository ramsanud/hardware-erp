package com.hardware.erp.supplier.dto;

import com.hardware.erp.supplier.entity.SupplierStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "SupplierResponse")
public record SupplierResponse(

        @Schema(example = "7") Long id,
        @Schema(example = "SUP-0007") String supplierCode,
        @Schema(example = "Sri Balaji Hardware Agencies") String supplierName,
        @Schema(example = "Ramesh Kumar") String contactPerson,
        @Schema(example = "9842011223") String mobileNo,
        @Schema(example = "9842011224") String alternateMobileNo,
        @Schema(example = "sales@sribalajihardware.in") String email,
        @Schema(example = "33AABCS1429B1ZP") String gstNo,
        @Schema(example = "AABCS1429B") String panNo,

        @Schema(example = "144 Big Bazaar Street") String addressLine1,
        @Schema(example = "Near Central Market") String addressLine2,
        @Schema(example = "Madurai") String city,
        @Schema(example = "33") String stateCode,
        @Schema(example = "625001") String pincode,

        @Schema(example = "30") Integer paymentTermsDays,
        @Schema(description = "Credit ceiling in paise", example = "50000000")
        Long creditLimitPaise,
        @Schema(description = "Same value formatted for display", example = "5,00,000.00")
        String creditLimitDisplay,

        @Schema(example = "Sri Balaji Hardware Agencies") String bankAccountName,
        @Schema(description = "Masked. Only the last four digits are returned.",
                example = "XXXXXXXXXX7890") String bankAccountNo,
        @Schema(example = "HDFC0001234") String bankIfsc,
        @Schema(example = "HDFC Bank") String bankName,

        @Schema(example = "ACTIVE") SupplierStatus status,
        @Schema(example = "Reliable for locks and door closers.") String remarks,
        List<SupplierContactResponse> contacts,

        @Schema(example = "2026-08-14T09:14:22.331") LocalDateTime createdAt,
        @Schema(example = "2026-08-14T09:14:22.331") LocalDateTime updatedAt
) {}
