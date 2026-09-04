package com.hardware.erp.supportticket.dto;

import com.hardware.erp.supportticket.entity.MessageAuthorType;

import java.time.LocalDateTime;

public record TicketMessageResponse(
        Long id,
        MessageAuthorType authorType,
        String authorName,
        String message,
        boolean internal,
        LocalDateTime createdAt
) {}
