package com.hardware.erp.project.dto;

import com.hardware.erp.project.entity.ProjectExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "ProjectExpenseRequest")
public record ProjectExpenseRequest(
        @NotNull(message = "Category is required")
        ProjectExpenseCategory category,

        @NotNull(message = "Amount is required")
        @Min(value = 0, message = "Amount cannot be negative")
        Long amountPaise,

        @NotNull(message = "Date is required")
        LocalDate expenseDate,

        @Schema(example = "Ganesh Labour Team")
        @Size(max = 200)
        String paidTo,

        @Size(max = 500)
        String description
) {}
