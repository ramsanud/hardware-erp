package com.hardware.erp.project.service;

import com.hardware.erp.project.dto.ProjectExpenseRequest;
import com.hardware.erp.project.dto.ProjectExpenseResponse;

import java.util.List;

public interface ProjectExpenseService {

    List<ProjectExpenseResponse> list(Long projectId);

    ProjectExpenseResponse add(Long projectId, ProjectExpenseRequest request);

    void remove(Long projectId, Long expenseId);
}
