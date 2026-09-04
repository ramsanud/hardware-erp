package com.hardware.erp.supportticket.dto;

import com.hardware.erp.supportticket.entity.TicketCategory;
import com.hardware.erp.supportticket.entity.TicketPriority;
import com.hardware.erp.supportticket.entity.TicketStatus;

import java.time.LocalDateTime;

public record SupportTicketSummaryResponse(
        Long id,
        /** Null on the tenant-facing list (a shop does not need to see its own name repeated); populated on the platform-admin list. */
        String tenantName,
        String subject,
        TicketCategory category,
        TicketPriority priority,
        TicketStatus status,
        Long assignedAdminId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
