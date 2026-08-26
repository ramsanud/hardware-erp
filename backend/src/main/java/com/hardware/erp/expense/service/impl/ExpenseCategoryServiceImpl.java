package com.hardware.erp.expense.service.impl;

import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.expense.dto.ExpenseCategoryRequest;
import com.hardware.erp.expense.dto.ExpenseCategoryResponse;
import com.hardware.erp.expense.entity.ExpenseCategory;
import com.hardware.erp.expense.mapper.ExpenseMapper;
import com.hardware.erp.expense.repository.ExpenseCategoryRepository;
import com.hardware.erp.expense.service.ExpenseCategoryService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Deliberately a plain user-extensible list, not a Java enum - see ExpenseCategory's own class comment. */
@Service
@RequiredArgsConstructor
public class ExpenseCategoryServiceImpl implements ExpenseCategoryService {

    private final ExpenseCategoryRepository categoryRepository;
    private final TenantRepository tenantRepository;
    private final ExpenseMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> list() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return categoryRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public ExpenseCategoryResponse create(ExpenseCategoryRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndTenantId(name, tenantId)) {
            throw new DuplicateResourceException("Expense category", name);
        }
        ExpenseCategory category = ExpenseCategory.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .name(name)
                .description(blankToNull(request.description()))
                .build();
        return mapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public ExpenseCategoryResponse update(Long id, ExpenseCategoryRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        ExpenseCategory category = categoryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense category", id));
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndTenantIdAndIdNot(name, tenantId, id)) {
            throw new DuplicateResourceException("Expense category", name);
        }
        category.setName(name);
        category.setDescription(blankToNull(request.description()));
        return mapper.toResponse(categoryRepository.save(category));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
