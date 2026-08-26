package com.hardware.erp.project.service.impl;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.project.dto.ProjectExpenseRequest;
import com.hardware.erp.project.dto.ProjectExpenseResponse;
import com.hardware.erp.project.entity.Project;
import com.hardware.erp.project.entity.ProjectExpense;
import com.hardware.erp.project.mapper.ProjectMapper;
import com.hardware.erp.project.repository.ProjectExpenseRepository;
import com.hardware.erp.project.service.ProjectExpenseService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** A manual ledger today - see ProjectExpense's own class comment for why. */
@Service
@RequiredArgsConstructor
public class ProjectExpenseServiceImpl implements ProjectExpenseService {

    private static final String MODULE = "PROJECT";
    private static final String ENTITY = "PROJECT_EXPENSE";

    private final ProjectExpenseRepository expenseRepository;
    private final ProjectServiceImpl projectService;
    private final TenantRepository tenantRepository;
    private final ActivityLogService activityLog;
    private final ProjectMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectExpenseResponse> list(Long projectId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        projectService.require(projectId, tenantId);
        return expenseRepository.findByProjectIdAndTenantIdOrderByExpenseDateDesc(projectId, tenantId).stream()
                .map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public ProjectExpenseResponse add(Long projectId, ProjectExpenseRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Project project = projectService.require(projectId, tenantId);

        ProjectExpense expense = ProjectExpense.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .project(project)
                .category(request.category())
                .amountPaise(request.amountPaise())
                .expenseDate(request.expenseDate())
                .paidTo(blankToNull(request.paidTo()))
                .description(blankToNull(request.description()))
                .createdAt(java.time.LocalDateTime.now())
                .build();

        ProjectExpense saved = expenseRepository.save(expense);
        activityLog.created(MODULE, ENTITY, saved.getId(), request.category() + " expense",
                Map.of("projectId", projectId, "amountPaise", saved.getAmountPaise()));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void remove(Long projectId, Long expenseId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        projectService.require(projectId, tenantId);
        ProjectExpense expense = expenseRepository.findByIdAndProjectIdAndTenantId(expenseId, projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project expense", expenseId));
        expenseRepository.delete(expense);
        activityLog.deleted(MODULE, ENTITY, expenseId, expense.getCategory() + " expense",
                "Removed from project " + projectId);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
