package com.hardware.erp.project.service;

import com.hardware.erp.project.dto.ProjectPaymentRequest;
import com.hardware.erp.project.dto.ProjectPaymentResponse;

import java.util.List;

public interface ProjectPaymentService {

    List<ProjectPaymentResponse> list(Long projectId);

    ProjectPaymentResponse add(Long projectId, ProjectPaymentRequest request);
}
