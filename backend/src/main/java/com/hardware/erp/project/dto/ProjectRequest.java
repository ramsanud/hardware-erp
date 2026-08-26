package com.hardware.erp.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Schema(name = "ProjectRequest", description = "Create or update a project")
public record ProjectRequest(
        @Schema(example = "Ram Sangar's Modular Kitchen")
        @NotBlank(message = "Project name is required")
        @Size(max = 200, message = "Project name must be 200 characters or fewer")
        String projectName,

        @Schema(description = "An existing customer id - create the customer first if needed", example = "6")
        @NotNull(message = "Customer is required")
        Long customerId,

        @Schema(description = "An existing work type id - use \"Add work type\" first if it doesn't exist yet", example = "1")
        @NotNull(message = "Work type is required")
        Long workTypeId,

        @Schema(example = "Full kitchen fit-out, SS countertop, 2 wall units")
        @Size(max = 2000)
        String description,

        @Schema(example = "12 Gandhi Street, Madurai")
        @Size(max = 500)
        String siteAddress,

        LocalDate startDate,

        LocalDate expectedCompletionDate,

        @Schema(description = "The date the customer expects delivery by - drives the overdue warning")
        LocalDate customerDeadline,

        @Schema(description = "The agreed contract value in paise - profit is calculated against this",
                example = "15000000")
        @NotNull(message = "Project value is required")
        @Min(value = 0, message = "Project value cannot be negative")
        Long projectValuePaise,

        @Schema(description = "An app_user id to act as project manager - optional", example = "3")
        Long managerUserId,

        @Schema(example = "Customer wants soft-close hinges throughout.")
        @Size(max = 2000)
        String notes
) {}
