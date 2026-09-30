package com.hardware.erp.invoice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** CR-091 Phase 3. A cancellation without a reason is not an audit trail. */
@Schema(name = "InvoiceCancelRequest")
public record InvoiceCancelRequest(
        @Schema(example = "Customer returned the goods at the counter before leaving")
        @NotBlank(message = "A reason is required to cancel an invoice")
        @Size(max = 255) String reason
) {}
