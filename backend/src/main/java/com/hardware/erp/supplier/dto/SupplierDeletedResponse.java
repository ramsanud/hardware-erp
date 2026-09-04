package com.hardware.erp.supplier.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * CR-058. The recycle-bin projection: enough to recognise the record and
 * decide whether to restore it, and nothing more. Deliberately narrower than
 * SupplierSummaryResponse - a deleted row is being reviewed, not traded with,
 * so credit limits, GST numbers and contact details have no business leaving
 * the server for this screen.
 */
@Schema(name = "SupplierDeletedResponse")
public record SupplierDeletedResponse(

        @Schema(example = "13") Long id,
        @Schema(example = "SUP-0013") String supplierCode,
        @Schema(example = "Old Ganesh Hardware") String supplierName,
        @Schema(example = "9840199887") String mobileNo,
        @Schema(example = "Madurai") String city,
        @Schema(example = "2026-08-30T11:04:00.000") LocalDateTime deletedAt
) {}
