package com.hardware.erp.supplier.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SupplierContactResponse")
public record SupplierContactResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "Ramesh Kumar") String contactName,
        @Schema(example = "Sales Manager") String designation,
        @Schema(example = "9842011223") String mobileNo,
        @Schema(example = "ramesh@sribalajihardware.in") String email,
        @Schema(example = "true") boolean primary
) {}
