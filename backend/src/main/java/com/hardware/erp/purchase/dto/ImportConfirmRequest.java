package com.hardware.erp.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ImportConfirmRequest(
        @NotNull Long supplierId,
        @Size(max = 60) String supplierBillNumber,
        @NotNull LocalDate purchaseDate,
        @NotEmpty @Valid List<ImportConfirmRow> rows,
        /** True only on a resubmission after the user saw a "possible duplicate bill" warning and chose Continue Anyway (spec §14). */
        boolean confirmDuplicateAnyway
) {}
