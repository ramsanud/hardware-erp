package com.hardware.erp.labour.dto;

import java.time.LocalDate;

public record WorkerWageSummaryResponse(
        Long workerId,
        String workerName,
        LocalDate fromDate,
        LocalDate toDate,
        long wageEarnedPaise,
        String wageEarnedDisplay,
        long paidPaise,
        String paidDisplay,
        long balancePaise,
        String balanceDisplay
) {}
