package com.hardware.erp.product.service.impl;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.product.dto.BrandRequest;
import com.hardware.erp.product.dto.BrandResponse;
import com.hardware.erp.product.entity.Brand;
import com.hardware.erp.product.mapper.BrandMapper;
import com.hardware.erp.product.repository.BrandRepository;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.product.service.BrandService;
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
public class BrandServiceImpl implements BrandService {

    private static final String MODULE = "PRODUCT";
    private static final String ENTITY = "BRAND";

    private final BrandRepository brandRepository;
    private final DocumentSequenceService documentSequenceService;
    private final ProductRepository productRepository;
    private final BrandMapper brandMapper;
    private final ActivityLogService activityLog;
    private final TenantRepository tenantRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BrandResponse> findAll() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return brandRepository.findAllByTenantIdOrderByBrandNameAsc(tenantId).stream()
                .map(brand -> brandMapper.toResponse(brand,
                        productRepository.countByBrandIdAndTenantId(brand.getId(), tenantId)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BrandResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Brand brand = require(id, tenantId);
        return brandMapper.toResponse(brand, productRepository.countByBrandIdAndTenantId(id, tenantId));
    }

    @Override
    @Transactional
    public BrandResponse create(BrandRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        String code = resolveCode(request.brandCode(), tenantId);
        String name = request.brandName().trim();

        if (brandRepository.existsByBrandCodeAndTenantId(code, tenantId)) {
            throw new DuplicateResourceException("Brand code", code);
        }
        if (brandRepository.existsByBrandNameIgnoreCaseAndTenantId(name, tenantId)) {
            throw new DuplicateResourceException("Brand name", name);
        }

        Brand brand = Brand.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .brandCode(code)
                .brandName(name)
                .description(blankToNull(request.description()))
                .status(request.status())
                .build();

        Brand saved = brandRepository.save(brand);
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getBrandName(), snapshot(saved));
        return brandMapper.toResponse(saved, 0);
    }

    @Override
    @Transactional
    public BrandResponse update(Long id, BrandRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Brand brand = require(id, tenantId);
        Map<String, Object> before = snapshot(brand);
        String name = request.brandName().trim();

        if (request.brandCode() != null && !request.brandCode().isBlank()
                && brandRepository.existsByBrandCodeAndTenantIdAndIdNot(request.brandCode(), tenantId, id)) {
            throw new DuplicateResourceException("Brand code", request.brandCode());
        }
        if (brandRepository.existsByBrandNameIgnoreCaseAndTenantIdAndIdNot(name, tenantId, id)) {
            throw new DuplicateResourceException("Brand name", name);
        }

        if (request.brandCode() != null && !request.brandCode().isBlank()) {
            brand.setBrandCode(request.brandCode().trim());
        }
        brand.setBrandName(name);
        brand.setDescription(blankToNull(request.description()));
        brand.setStatus(request.status());

        Brand saved = brandRepository.save(brand);
        activityLog.updated(MODULE, ENTITY, id, saved.getBrandName(), before, snapshot(saved));
        return brandMapper.toResponse(saved, productRepository.countByBrandIdAndTenantId(id, tenantId));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Brand brand = require(id, tenantId);

        long productCount = productRepository.countByBrandIdAndTenantId(id, tenantId);
        if (productCount > 0) {
            throw new BusinessException(
                    "%d product(s) still use this brand. Reassign them first.".formatted(productCount));
        }

        brandRepository.delete(brand);
        activityLog.deleted(MODULE, ENTITY, id, brand.getBrandName(), "Removed. No products referenced it.");
    }

    private Map<String, Object> snapshot(Brand brand) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("brandCode", brand.getBrandCode());
        values.put("brandName", brand.getBrandName());
        values.put("status", brand.getStatus() == null ? null : brand.getStatus().name());
        return values;
    }

    private Brand require(Long id, Long tenantId) {
        return brandRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", id));
    }

    private String resolveCode(String requested, Long tenantId) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim().toUpperCase();
        }
        return documentSequenceService.next(DocumentType.BRAND, tenantId);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
