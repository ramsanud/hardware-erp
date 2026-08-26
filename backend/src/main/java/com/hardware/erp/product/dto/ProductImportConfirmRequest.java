package com.hardware.erp.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ProductImportConfirmRequest(
        @NotEmpty @Valid List<ProductImportConfirmRow> rows
) {}
