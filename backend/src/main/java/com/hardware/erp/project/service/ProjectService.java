package com.hardware.erp.project.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.project.dto.ProjectRequest;
import com.hardware.erp.project.dto.ProjectResponse;
import com.hardware.erp.project.dto.ProjectStatusChangeRequest;
import com.hardware.erp.project.dto.ProjectSummaryResponse;
import com.hardware.erp.project.entity.ProjectStatus;
import org.springframework.data.domain.Pageable;

public interface ProjectService {

    ProjectResponse create(ProjectRequest request);

    ProjectResponse update(Long id, ProjectRequest request);

    ProjectResponse get(Long id);

    PageResponse<ProjectSummaryResponse> search(String search, ProjectStatus status, Long customerId, Pageable pageable);

    ProjectResponse changeStatus(Long id, ProjectStatusChangeRequest request);
}
