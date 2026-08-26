package com.hardware.erp.supplier.dto;

import com.hardware.erp.supplier.entity.SupplierStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The list-screen projection.
 *
 * Deliberately omits bank details and remarks: a list of 100 suppliers should
 * not ship 100 sets of bank account data to the browser when the table shows
 * six columns.
 */
@Schema(name = "SupplierSummaryResponse")
public record SupplierSummaryResponse(

        @Schema(example = "7") Long id,
        @Schema(example = "SUP-0007") String supplierCode,
        @Schema(example = "Sri Balaji Hardware Agencies") String supplierName,
        @Schema(example = "Ramesh Kumar") String contactPerson,
        @Schema(example = "9842011223") String mobileNo,
        @Schema(example = "Madurai") String city,
        @Schema(example = "33AABCS1429B1ZP") String gstNo,
        @Schema(example = "30") Integer paymentTermsDays,
        @Schema(example = "5,00,000.00") String creditLimitDisplay,
        @Schema(example = "ACTIVE") SupplierStatus status
) {}
