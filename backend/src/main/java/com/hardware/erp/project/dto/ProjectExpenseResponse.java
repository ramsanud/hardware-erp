package com.hardware.erp.project.dto;

import com.hardware.erp.project.entity.ProjectExpenseCategory;

import java.time.LocalDate;

public record ProjectExpenseResponse(
        Long id,
        ProjectExpenseCategory category,
        String amountDisplay,
        LocalDate expenseDate,
        String paidTo,
        String description
) {}
