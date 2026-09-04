package com.hardware.erp.notification.dto;

import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "NotificationLogResponse")
public record NotificationLogResponse(

        @Schema(example = "101") Long id,
        @Schema(example = "SMS") NotificationChannel channel,
        @Schema(example = "9876500001") String recipient,
        @Schema(example = "Invoice INV-000042") String subject,
        @Schema(example = "Your invoice INV-000042 for ₹1,234.00 has been generated.") String body,
        @Schema(example = "LOGGED_ONLY") NotificationStatus status,
        @Schema(example = "INVOICE") String relatedEntityType,
        @Schema(example = "42") Long relatedEntityId,
        @Schema(description = "The provider's own message id on a real send (e.g. WhatsApp's wamid). Null for LOGGED_ONLY/FAILED.", example = "wamid.HBgLOTE5ODc2NTAwMDAxFQIAERgSNzY2RjQ4RTdBRUY0RjNCQzk1AA==")
        String providerMessageId,
        @Schema(example = "2026-08-22T09:14:22.331") LocalDateTime createdAt
) {}
