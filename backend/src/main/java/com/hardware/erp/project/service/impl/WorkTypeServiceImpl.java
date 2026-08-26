package com.hardware.erp.project.service.impl;

import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.project.dto.WorkTypeRequest;
import com.hardware.erp.project.dto.WorkTypeResponse;
import com.hardware.erp.project.entity.WorkType;
import com.hardware.erp.project.mapper.ProjectMapper;
import com.hardware.erp.project.repository.WorkTypeRepository;
import com.hardware.erp.project.service.WorkTypeService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Deliberately a plain user-extensible list, not a Java enum - see WorkType's own class comment. */
@Service
@RequiredArgsConstructor
public class WorkTypeServiceImpl implements WorkTypeService {

    private final WorkTypeRepository workTypeRepository;
    private final TenantRepository tenantRepository;
    private final ProjectMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<WorkTypeResponse> list() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return workTypeRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public WorkTypeResponse create(WorkTypeRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        String name = request.name().trim();
        if (workTypeRepository.existsByNameIgnoreCaseAndTenantId(name, tenantId)) {
            throw new DuplicateResourceException("Work type", name);
        }
        WorkType workType = WorkType.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .name(name)
                .description(blankToNull(request.description()))
                .build();
        return mapper.toResponse(workTypeRepository.save(workType));
    }

    @Override
    @Transactional
    public WorkTypeResponse update(Long id, WorkTypeRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        WorkType workType = workTypeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Work type", id));
        String name = request.name().trim();
        if (workTypeRepository.existsByNameIgnoreCaseAndTenantIdAndIdNot(name, tenantId, id)) {
            throw new DuplicateResourceException("Work type", name);
        }
        workType.setName(name);
        workType.setDescription(blankToNull(request.description()));
        return mapper.toResponse(workTypeRepository.save(workType));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
