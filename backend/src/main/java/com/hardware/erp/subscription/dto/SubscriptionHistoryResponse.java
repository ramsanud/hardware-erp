package com.hardware.erp.subscription.dto;

import java.time.LocalDateTime;

public record SubscriptionHistoryResponse(String fromPlanCode, String toPlanCode, String fromStatus, String toStatus,
                                          String reason, LocalDateTime createdAt) {}
