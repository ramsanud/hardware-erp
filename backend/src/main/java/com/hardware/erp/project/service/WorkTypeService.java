package com.hardware.erp.project.service;

import com.hardware.erp.project.dto.WorkTypeRequest;
import com.hardware.erp.project.dto.WorkTypeResponse;

import java.util.List;

public interface WorkTypeService {

    List<WorkTypeResponse> list();

    WorkTypeResponse create(WorkTypeRequest request);

    WorkTypeResponse update(Long id, WorkTypeRequest request);
}
