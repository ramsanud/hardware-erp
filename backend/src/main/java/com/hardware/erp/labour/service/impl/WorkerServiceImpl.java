package com.hardware.erp.labour.service.impl;

import com.hardware.erp.common.activity.ActivityAction;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.labour.dto.WorkerRequest;
import com.hardware.erp.labour.dto.WorkerResponse;
import com.hardware.erp.labour.entity.Worker;
import com.hardware.erp.labour.entity.WorkerStatus;
import com.hardware.erp.labour.mapper.LabourMapper;
import com.hardware.erp.labour.repository.WorkerRepository;
import com.hardware.erp.labour.service.WorkerService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkerServiceImpl implements WorkerService {

    private static final String MODULE = "LABOUR";
    private static final String ENTITY = "WORKER";

    private final WorkerRepository workerRepository;
    private final TenantRepository tenantRepository;
    private final LabourMapper mapper;
    private final ActivityLogService activityLog;

    @Override
    @Transactional
    public WorkerResponse create(WorkerRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        String mobileNo = blankToNull(request.mobileNo());
        if (mobileNo != null && workerRepository.existsByTenantIdAndMobileNo(tenantId, mobileNo)) {
            throw new DuplicateResourceException("Worker mobile number", mobileNo);
        }
        Worker worker = Worker.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .name(request.name().trim())
                .mobileNo(mobileNo)
                .roleTitle(blankToNull(request.roleTitle()))
                .dailyRatePaise(request.dailyRatePaise())
                .status(WorkerStatus.ACTIVE)
                .build();

        Worker saved = workerRepository.save(worker);
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getName(), snapshot(saved));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public WorkerResponse update(Long id, WorkerRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Worker worker = require(id, tenantId);
        Map<String, Object> before = snapshot(worker);

        String mobileNo = blankToNull(request.mobileNo());
        if (mobileNo != null) {
            workerRepository.findByTenantIdAndMobileNo(tenantId, mobileNo)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new DuplicateResourceException("Worker mobile number", mobileNo);
                    });
        }

        worker.setName(request.name().trim());
        worker.setMobileNo(mobileNo);
        worker.setRoleTitle(blankToNull(request.roleTitle()));
        worker.setDailyRatePaise(request.dailyRatePaise());

        Worker saved = workerRepository.save(worker);
        activityLog.updated(MODULE, ENTITY, id, saved.getName(), before, snapshot(saved));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkerResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return mapper.toResponse(require(id, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WorkerResponse> search(String search, WorkerStatus status, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                workerRepository.search(tenantId, blankToNull(search), status, pageable),
                mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerResponse> listActive() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return workerRepository.findByTenantIdAndStatusOrderByNameAsc(tenantId, WorkerStatus.ACTIVE)
                .stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Worker worker = require(id, tenantId);
        worker.setStatus(WorkerStatus.INACTIVE);
        workerRepository.save(worker);
        activityLog.deleted(MODULE, ENTITY, id, worker.getName(), "Worker deactivated");
    }

    @Override
    @Transactional
    public void activate(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Worker worker = require(id, tenantId);
        worker.setStatus(WorkerStatus.ACTIVE);
        workerRepository.save(worker);
        activityLog.action(MODULE, ENTITY, id, worker.getName(),
                ActivityAction.STATUS_CHANGE, "Worker reactivated");
    }

    private Worker require(Long id, Long tenantId) {
        return workerRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", id));
    }

    private Map<String, Object> snapshot(Worker worker) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", worker.getName());
        values.put("mobileNo", worker.getMobileNo());
        values.put("roleTitle", worker.getRoleTitle());
        values.put("dailyRatePaise", worker.getDailyRatePaise());
        values.put("status", worker.getStatus());
        return values;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
