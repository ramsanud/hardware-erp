package com.hardware.erp.labour.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.labour.dto.WorkerRequest;
import com.hardware.erp.labour.dto.WorkerResponse;
import com.hardware.erp.labour.entity.WorkerStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface WorkerService {

    WorkerResponse create(WorkerRequest request);

    WorkerResponse update(Long id, WorkerRequest request);

    WorkerResponse get(Long id);

    PageResponse<WorkerResponse> search(String search, WorkerStatus status, Pageable pageable);

    /** Active workers only, for attendance-marking and payment pickers. */
    List<WorkerResponse> listActive();

    void deactivate(Long id);

    void activate(Long id);
}
