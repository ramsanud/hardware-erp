package com.hardware.erp.product.service.impl;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.product.dto.CategoryRequest;
import com.hardware.erp.product.dto.CategoryResponse;
import com.hardware.erp.product.entity.Category;
import com.hardware.erp.product.mapper.CategoryMapper;
import com.hardware.erp.product.repository.CategoryRepository;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.product.service.CategoryService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final String MODULE = "PRODUCT";
    private static final String ENTITY = "CATEGORY";

    private final CategoryRepository categoryRepository;
    private final DocumentSequenceService documentSequenceService;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;
    private final ActivityLogService activityLog;
    private final TenantRepository tenantRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return categoryRepository.findAllByTenantIdOrderByCategoryNameAsc(tenantId).stream()
                .map(category -> categoryMapper.toResponse(category,
                        productRepository.countByCategoryIdAndTenantId(category.getId(), tenantId)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Category category = require(id, tenantId);
        return categoryMapper.toResponse(category,
                productRepository.countByCategoryIdAndTenantId(id, tenantId));
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        String code = resolveCode(request.categoryCode(), tenantId);
        String name = request.categoryName().trim();

        if (categoryRepository.existsByCategoryCodeAndTenantId(code, tenantId)) {
            throw new DuplicateResourceException("Category code", code);
        }
        if (categoryRepository.existsByCategoryNameIgnoreCaseAndTenantId(name, tenantId)) {
            throw new DuplicateResourceException("Category name", name);
        }

        Category category = Category.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .categoryCode(code)
                .categoryName(name)
                .parent(resolveParent(request.parentCategoryId(), tenantId, null))
                .description(blankToNull(request.description()))
                .status(request.status())
                .build();

        Category saved = categoryRepository.save(category);
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getCategoryName(), snapshot(saved));
        return categoryMapper.toResponse(saved, 0);
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Category category = require(id, tenantId);
        Map<String, Object> before = snapshot(category);
        String name = request.categoryName().trim();

        if (request.categoryCode() != null && !request.categoryCode().isBlank()
                && categoryRepository.existsByCategoryCodeAndTenantIdAndIdNot(
                        request.categoryCode(), tenantId, id)) {
            throw new DuplicateResourceException("Category code", request.categoryCode());
        }
        if (categoryRepository.existsByCategoryNameIgnoreCaseAndTenantIdAndIdNot(name, tenantId, id)) {
            throw new DuplicateResourceException("Category name", name);
        }

        if (request.categoryCode() != null && !request.categoryCode().isBlank()) {
            category.setCategoryCode(request.categoryCode().trim());
        }
        category.setCategoryName(name);
        category.setParent(resolveParent(request.parentCategoryId(), tenantId, id));
        category.setDescription(blankToNull(request.description()));
        category.setStatus(request.status());

        Category saved = categoryRepository.save(category);
        activityLog.updated(MODULE, ENTITY, id, saved.getCategoryName(), before, snapshot(saved));
        return categoryMapper.toResponse(saved,
                productRepository.countByCategoryIdAndTenantId(id, tenantId));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Category category = require(id, tenantId);

        long productCount = productRepository.countByCategoryIdAndTenantId(id, tenantId);
        if (productCount > 0) {
            throw new BusinessException(
                    "%d product(s) still use this category. Reassign them first."
                            .formatted(productCount));
        }
        long childCount = categoryRepository.countByParentIdAndTenantId(id, tenantId);
        if (childCount > 0) {
            throw new BusinessException(
                    "%d sub-categor%s still nested under this one. Reassign or remove them first."
                            .formatted(childCount, childCount == 1 ? "y is" : "ies are"));
        }

        categoryRepository.delete(category);
        activityLog.deleted(MODULE, ENTITY, id, category.getCategoryName(), "Removed. No products referenced it.");
    }

    private Category resolveParent(Long parentId, Long tenantId, Long selfId) {
        if (parentId == null) {
            return null;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BusinessException("A category cannot be its own parent");
        }
        return categoryRepository.findByIdAndTenantId(parentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category", parentId));
    }

    private Map<String, Object> snapshot(Category category) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("categoryCode", category.getCategoryCode());
        values.put("categoryName", category.getCategoryName());
        values.put("parentCategoryId", category.getParent() == null ? null : category.getParent().getId());
        values.put("status", category.getStatus() == null ? null : category.getStatus().name());
        return values;
    }

    private Category require(Long id, Long tenantId) {
        return categoryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private String resolveCode(String requested, Long tenantId) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim().toUpperCase();
        }
        return documentSequenceService.next(DocumentType.CATEGORY, tenantId);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
