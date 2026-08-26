package com.hardware.erp.labour.dto;

import com.hardware.erp.labour.entity.WorkerStatus;

import java.time.LocalDateTime;

public record WorkerResponse(
        Long id,
        String name,
        String mobileNo,
        String roleTitle,
        Long dailyRatePaise,
        String dailyRateDisplay,
        WorkerStatus status,
        LocalDateTime createdAt
) {}
