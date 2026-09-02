package com.hardware.erp.supportticket.dto;

import com.hardware.erp.supportticket.entity.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 4000) String description,
        @NotNull TicketCategory category
) {}
