package com.hardware.erp.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExpenseCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
) {}
