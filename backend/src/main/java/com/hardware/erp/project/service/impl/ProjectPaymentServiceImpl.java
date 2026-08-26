package com.hardware.erp.project.service.impl;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.project.dto.ProjectPaymentRequest;
import com.hardware.erp.project.dto.ProjectPaymentResponse;
import com.hardware.erp.project.entity.Project;
import com.hardware.erp.project.entity.ProjectPayment;
import com.hardware.erp.project.mapper.ProjectMapper;
import com.hardware.erp.project.repository.ProjectPaymentRepository;
import com.hardware.erp.project.service.ProjectPaymentService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectPaymentServiceImpl implements ProjectPaymentService {

    private static final String MODULE = "PROJECT";
    private static final String ENTITY = "PROJECT_PAYMENT";

    private final ProjectPaymentRepository paymentRepository;
    private final ProjectServiceImpl projectService;
    private final TenantRepository tenantRepository;
    private final ActivityLogService activityLog;
    private final ProjectMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectPaymentResponse> list(Long projectId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        projectService.require(projectId, tenantId);
        return paymentRepository.findByProjectIdAndTenantIdOrderByPaymentDateDesc(projectId, tenantId).stream()
                .map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public ProjectPaymentResponse add(Long projectId, ProjectPaymentRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Project project = projectService.require(projectId, tenantId);

        ProjectPayment payment = ProjectPayment.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .project(project)
                .amountPaise(request.amountPaise())
                .paymentMethod(request.paymentMethod())
                .paymentDate(request.paymentDate())
                .notes(blankToNull(request.notes()))
                .createdAt(java.time.LocalDateTime.now())
                .build();

        ProjectPayment saved = paymentRepository.save(payment);
        activityLog.created(MODULE, ENTITY, saved.getId(), "Payment received",
                Map.of("projectId", projectId, "amountPaise", saved.getAmountPaise()));
        return mapper.toResponse(saved);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
