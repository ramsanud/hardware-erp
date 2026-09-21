package com.hardware.erp.sync.dto;

import com.hardware.erp.sync.entity.SyncTransactionStatus;
import com.hardware.erp.sync.entity.SyncTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** CR-091 Phase 9. The server side of offline sync - one batch upload, one outcome per transaction. */
public final class SyncDtos {

    private SyncDtos() {
    }

    @Schema(name = "SyncTransactionRequest")
    public record SyncTransactionRequest(
            @Schema(description = "Client-generated, stable across retries - the idempotency key.")
            @NotNull UUID clientUuid,
            @NotBlank @Size(max = 100) String deviceId,
            @NotNull SyncTransactionType transactionType,
            @NotNull LocalDateTime clientCreatedAt,
            @Schema(description = "For INVOICE: the same shape as POST /v1/invoices' body.")
            @NotNull Map<String, Object> payload
    ) {}

    @Schema(name = "SyncBatchRequest")
    public record SyncBatchRequest(
            @NotEmpty @Valid List<SyncTransactionRequest> transactions
    ) {}

    @Schema(name = "SyncTransactionResult")
    public record SyncTransactionResult(
            UUID clientUuid,
            SyncTransactionStatus status,
            @Schema(description = "True when this UUID had already been synced - the stored result is returned, nothing new was created.")
            boolean replay,
            String resultReferenceType,
            Long resultReferenceId,
            String resultReferenceNumber,
            String conflictReason,
            String errorMessage
    ) {}

    @Schema(name = "SyncBatchResponse")
    public record SyncBatchResponse(List<SyncTransactionResult> results) {}
}
