package com.hardware.erp.supportticket.dto;

import com.hardware.erp.supportticket.entity.TicketCategory;
import com.hardware.erp.supportticket.entity.TicketPriority;
import com.hardware.erp.supportticket.entity.TicketStatus;

import java.time.LocalDateTime;
import java.util.List;

public record SupportTicketDetailResponse(
        Long id,
        String tenantName,
        String raisedByName,
        String subject,
        String description,
        TicketCategory category,
        TicketPriority priority,
        TicketStatus status,
        Long assignedAdminId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime resolvedAt,
        /** Internal notes are already filtered out before this reaches a tenant-facing response - see SupportTicketService. */
        List<TicketMessageResponse> messages
) {}
