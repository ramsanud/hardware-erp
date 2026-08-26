package com.hardware.erp.project.service.impl;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.common.activity.ActivityAction;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.repository.CustomerRepository;
import com.hardware.erp.labour.repository.WorkerAttendanceRepository;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.project.dto.ProjectRequest;
import com.hardware.erp.project.dto.ProjectResponse;
import com.hardware.erp.project.dto.ProjectStatusChangeRequest;
import com.hardware.erp.project.dto.ProjectSummaryResponse;
import com.hardware.erp.project.entity.Project;
import com.hardware.erp.project.entity.ProjectStatus;
import com.hardware.erp.project.entity.WorkType;
import com.hardware.erp.project.mapper.ProjectMapper;
import com.hardware.erp.project.repository.ProjectExpenseRepository;
import com.hardware.erp.project.repository.ProjectMaterialRepository;
import com.hardware.erp.project.repository.ProjectPaymentRepository;
import com.hardware.erp.project.repository.ProjectRepository;
import com.hardware.erp.project.repository.WorkTypeRepository;
import com.hardware.erp.project.service.ProjectService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Depends On:
 *   Customer Module - every project belongs to a customer
 *   Auth Module - optional project manager reference
 *
 * Profit/loss is computed fresh on every read from project_material +
 * project_expense + project_payment - never stored, never trusted from the
 * client (request §7). Revenue is the agreed project_value_paise, not the
 * sum of payments received - a project earns revenue as work completes,
 * not as cash arrives; balance receivable is the separate, distinct figure
 * for "how much cash is still owed".
 */
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private static final String MODULE = "PROJECT";
    private static final String ENTITY = "PROJECT";

    private final ProjectRepository projectRepository;
    private final DocumentSequenceService documentSequenceService;
    private final WorkTypeRepository workTypeRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final ProjectMaterialRepository materialRepository;
    private final ProjectExpenseRepository expenseRepository;
    private final ProjectPaymentRepository paymentRepository;
    private final WorkerAttendanceRepository workerAttendanceRepository;
    private final ActivityLogService activityLog;
    private final ProjectMapper mapper;

    @Override
    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Customer customer = customerRepository.findByIdAndTenantId(request.customerId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));
        WorkType workType = workTypeRepository.findByIdAndTenantId(request.workTypeId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Work type", request.workTypeId()));
        User manager = resolveManager(request.managerUserId(), tenantId);

        String projectNumber = documentSequenceService.next(DocumentType.PROJECT, tenantId);

        Project project = Project.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .projectNumber(projectNumber)
                .projectName(request.projectName().trim())
                .customer(customer)
                .workType(workType)
                .description(blankToNull(request.description()))
                .siteAddress(blankToNull(request.siteAddress()))
                .startDate(request.startDate())
                .expectedCompletionDate(request.expectedCompletionDate())
                .customerDeadline(request.customerDeadline())
                .status(ProjectStatus.UPCOMING)
                .projectValuePaise(request.projectValuePaise())
                .managerUser(manager)
                .notes(blankToNull(request.notes()))
                .build();

        Project saved = projectRepository.save(project);
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getProjectName(), snapshot(saved));
        return toResponse(saved, tenantId);
    }

    @Override
    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Project project = require(id, tenantId);
        Map<String, Object> before = snapshot(project);

        Customer customer = customerRepository.findByIdAndTenantId(request.customerId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));
        WorkType workType = workTypeRepository.findByIdAndTenantId(request.workTypeId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Work type", request.workTypeId()));
        User manager = resolveManager(request.managerUserId(), tenantId);

        project.setProjectName(request.projectName().trim());
        project.setCustomer(customer);
        project.setWorkType(workType);
        project.setDescription(blankToNull(request.description()));
        project.setSiteAddress(blankToNull(request.siteAddress()));
        project.setStartDate(request.startDate());
        project.setExpectedCompletionDate(request.expectedCompletionDate());
        project.setCustomerDeadline(request.customerDeadline());
        project.setProjectValuePaise(request.projectValuePaise());
        project.setManagerUser(manager);
        project.setNotes(blankToNull(request.notes()));

        Project saved = projectRepository.save(project);
        activityLog.updated(MODULE, ENTITY, saved.getId(), saved.getProjectName(), before, snapshot(saved));
        return toResponse(saved, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return toResponse(require(id, tenantId), tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectSummaryResponse> search(String search, ProjectStatus status, Long customerId,
                                                        Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                projectRepository.search(tenantId, search, status, customerId, pageable),
                project -> mapper.toSummary(project,
                        materialRepository.sumTotalCostByProject(project.getId(), tenantId),
                        expenseRepository.sumAmountByProject(project.getId(), tenantId)));
    }

    /**
     * A state-machine transition, not a blind field write - COMPLETED
     * requires an outcome, every other status forbids one (the same rule
     * V18's ck_project_outcome_only_when_completed enforces at the
     * database layer; enforcing it here too gives a clean 422 instead of a
     * constraint-violation 500).
     */
    @Override
    @Transactional
    public ProjectResponse changeStatus(Long id, ProjectStatusChangeRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Project project = require(id, tenantId);

        if (request.status() == ProjectStatus.COMPLETED && request.outcome() == null) {
            throw new BusinessException("Mark this project as a success or a failure when completing it.",
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "OUTCOME_REQUIRED");
        }
        if (request.status() != ProjectStatus.COMPLETED && request.outcome() != null) {
            throw new BusinessException("Outcome can only be set when the project is being marked Completed.",
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "OUTCOME_NOT_ALLOWED");
        }

        ProjectStatus previous = project.getStatus();
        project.setStatus(request.status());
        project.setOutcome(request.outcome());
        if (request.status() == ProjectStatus.COMPLETED && project.getActualCompletionDate() == null) {
            project.setActualCompletionDate(LocalDate.now());
        }

        Project saved = projectRepository.save(project);
        activityLog.action(MODULE, ENTITY, saved.getId(), saved.getProjectName(), ActivityAction.STATUS_CHANGE,
                previous + " -> " + request.status() + (request.outcome() != null ? " (" + request.outcome() + ")" : ""));
        return toResponse(saved, tenantId);
    }

    private ProjectResponse toResponse(Project project, Long tenantId) {
        long materialCost = materialRepository.sumTotalCostByProject(project.getId(), tenantId);
        long expenseCost = expenseRepository.sumAmountByProject(project.getId(), tenantId);
        long received = paymentRepository.sumAmountByProject(project.getId(), tenantId);
        long labourCost = workerAttendanceRepository.sumWagePaiseByProject(tenantId, project.getId());
        return mapper.toResponse(project, materialCost, expenseCost, received, labourCost);
    }

    private User resolveManager(Long managerUserId, Long tenantId) {
        if (managerUserId == null) return null;
        return userRepository.findByIdAndTenantId(managerUserId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User", managerUserId));
    }

    Project require(Long id, Long tenantId) {
        return projectRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    private Map<String, Object> snapshot(Project project) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("projectName", project.getProjectName());
        map.put("customerId", project.getCustomer().getId());
        map.put("workTypeId", project.getWorkType().getId());
        map.put("status", project.getStatus());
        map.put("outcome", project.getOutcome());
        map.put("projectValuePaise", project.getProjectValuePaise());
        map.put("customerDeadline", project.getCustomerDeadline());
        return map;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
