package com.hardware.erp.inventory.dto;

import com.hardware.erp.inventory.entity.MovementType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(name = "StockMovementResponse")
public record StockMovementResponse(
        Long id,
        MovementType movementType,
        BigDecimal quantityChange,
        BigDecimal balanceAfter,
        String referenceType,
        Long referenceId,
        String notes,
        LocalDateTime createdAt
) {}
