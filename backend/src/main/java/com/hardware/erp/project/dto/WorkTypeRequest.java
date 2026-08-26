package com.hardware.erp.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "WorkTypeRequest", description = "Add or rename a project work type")
public record WorkTypeRequest(
        @Schema(example = "Rooftop Sheet")
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be 100 characters or fewer")
        String name,

        @Schema(example = "Roofing sheet + frame installation")
        @Size(max = 500)
        String description
) {}
