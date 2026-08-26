package com.hardware.erp.project.service;

import com.hardware.erp.project.dto.ProjectMaterialRequest;
import com.hardware.erp.project.dto.ProjectMaterialResponse;

import java.util.List;

public interface ProjectMaterialService {

    List<ProjectMaterialResponse> list(Long projectId);

    ProjectMaterialResponse add(Long projectId, ProjectMaterialRequest request);

    ProjectMaterialResponse update(Long projectId, Long materialId, ProjectMaterialRequest request);

    void remove(Long projectId, Long materialId);
}
