package com.hardware.erp.project.dto;

import com.hardware.erp.project.entity.ProjectOutcome;
import com.hardware.erp.project.entity.ProjectStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Every money figure here is computed server-side in ProjectServiceImpl -
 * never trust a client-supplied profit number (request §7).
 */
public record ProjectResponse(
        Long id,
        String projectNumber,
        String projectName,
        Long customerId,
        String customerName,
        Long workTypeId,
        String workTypeName,
        String description,
        String siteAddress,
        LocalDate startDate,
        LocalDate expectedCompletionDate,
        LocalDate actualCompletionDate,
        LocalDate customerDeadline,
        ProjectStatus status,
        ProjectOutcome outcome,
        boolean overdue,
        String projectValueDisplay,
        String totalMaterialCostDisplay,
        String totalExpenseCostDisplay,
        String totalCostDisplay,
        String netProfitDisplay,
        boolean profitPositive,
        String profitMarginPercentDisplay,
        String totalReceivedDisplay,
        String balanceReceivableDisplay,
        // Live-computed from worker_attendance x each worker's current daily
        // rate (CR-036 phase 4). Deliberately NOT folded into
        // totalCostDisplay/netProfitDisplay above - those stay exactly as
        // they were before Labour Monitor existed, computed only from
        // project_material + project_expense, so this addition never
        // silently changes a profit figure an owner already relies on.
        String totalLabourCostDisplay,
        Long managerUserId,
        String managerUserName,
        String notes,
        LocalDateTime createdAt
) {}
