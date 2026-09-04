package com.hardware.erp.supportticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** internal is ignored on the tenant-facing reply endpoint - a tenant user can never write an internal note. */
public record TicketMessageRequest(
        @NotBlank @Size(max = 4000) String message,
        boolean internal
) {}
