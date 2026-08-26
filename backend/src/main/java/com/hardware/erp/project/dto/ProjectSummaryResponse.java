package com.hardware.erp.project.dto;

import com.hardware.erp.project.entity.ProjectOutcome;
import com.hardware.erp.project.entity.ProjectStatus;

public record ProjectSummaryResponse(
        Long id,
        String projectNumber,
        String projectName,
        String customerName,
        String workTypeName,
        ProjectStatus status,
        ProjectOutcome outcome,
        boolean overdue,
        String projectValueDisplay,
        String netProfitDisplay,
        boolean profitPositive
) {}
