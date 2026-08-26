package com.hardware.erp.project.service.impl;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.project.dto.ProjectMaterialRequest;
import com.hardware.erp.project.dto.ProjectMaterialResponse;
import com.hardware.erp.project.entity.Project;
import com.hardware.erp.project.entity.ProjectMaterial;
import com.hardware.erp.project.mapper.ProjectMapper;
import com.hardware.erp.project.repository.ProjectMaterialRepository;
import com.hardware.erp.project.service.ProjectMaterialService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.supplier.entity.Supplier;
import com.hardware.erp.supplier.repository.SupplierRepository;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Depends On: Product Module (material product must exist and be
 * tenant-owned), Supplier Module (optional - see class comment on the
 * entity for why supplier is nullable).
 */
@Service
@RequiredArgsConstructor
public class ProjectMaterialServiceImpl implements ProjectMaterialService {

    private static final String MODULE = "PROJECT";
    private static final String ENTITY = "PROJECT_MATERIAL";

    private final ProjectMaterialRepository materialRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final ProjectServiceImpl projectService;
    private final TenantRepository tenantRepository;
    private final ActivityLogService activityLog;
    private final ProjectMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMaterialResponse> list(Long projectId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        projectService.require(projectId, tenantId);
        return materialRepository.findByProjectIdAndTenantIdOrderByIdAsc(projectId, tenantId).stream()
                .map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public ProjectMaterialResponse add(Long projectId, ProjectMaterialRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Project project = projectService.require(projectId, tenantId);
        Product product = productRepository.findByIdAndTenantId(request.productId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));
        Supplier supplier = resolveSupplier(request.supplierId(), tenantId);

        long unitPricePaise = request.unitPricePaise() != null ? request.unitPricePaise() : product.getSellingPricePaise();

        ProjectMaterial material = ProjectMaterial.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .project(project)
                .product(product)
                .supplier(supplier)
                .quantityRequired(request.quantityRequired())
                .quantityEstimated(request.quantityEstimated())
                .quantityActual(request.quantityActual())
                .quantityWastage(request.quantityWastage() != null ? request.quantityWastage() : BigDecimal.ZERO)
                .unit(product.getUnit())
                .unitPricePaise(unitPricePaise)
                .totalCostPaise(totalCost(unitPricePaise, request.quantityActual(), request.quantityRequired()))
                .notes(blankToNull(request.notes()))
                .createdAt(java.time.LocalDateTime.now())
                .build();

        ProjectMaterial saved = materialRepository.save(material);
        activityLog.created(MODULE, ENTITY, saved.getId(), product.getProductName(),
                java.util.Map.of("projectId", projectId, "productId", product.getId(),
                        "totalCostPaise", saved.getTotalCostPaise()));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProjectMaterialResponse update(Long projectId, Long materialId, ProjectMaterialRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        projectService.require(projectId, tenantId);
        ProjectMaterial material = materialRepository.findByIdAndProjectIdAndTenantId(materialId, projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project material", materialId));
        Product product = productRepository.findByIdAndTenantId(request.productId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));
        Supplier supplier = resolveSupplier(request.supplierId(), tenantId);

        long unitPricePaise = request.unitPricePaise() != null ? request.unitPricePaise() : product.getSellingPricePaise();

        material.setProduct(product);
        material.setSupplier(supplier);
        material.setQuantityRequired(request.quantityRequired());
        material.setQuantityEstimated(request.quantityEstimated());
        material.setQuantityActual(request.quantityActual());
        material.setQuantityWastage(request.quantityWastage() != null ? request.quantityWastage() : BigDecimal.ZERO);
        material.setUnit(product.getUnit());
        material.setUnitPricePaise(unitPricePaise);
        material.setTotalCostPaise(totalCost(unitPricePaise, request.quantityActual(), request.quantityRequired()));
        material.setNotes(blankToNull(request.notes()));
        material.setUpdatedAt(java.time.LocalDateTime.now());

        return mapper.toResponse(materialRepository.save(material));
    }

    @Override
    @Transactional
    public void remove(Long projectId, Long materialId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        projectService.require(projectId, tenantId);
        ProjectMaterial material = materialRepository.findByIdAndProjectIdAndTenantId(materialId, projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project material", materialId));
        materialRepository.delete(material);
        activityLog.deleted(MODULE, ENTITY, materialId, material.getProduct().getProductName(),
                "Removed from project " + projectId);
    }

    /** Cost is priced against the actual quantity used once known, falling back to the planned/required quantity before work starts. */
    private long totalCost(long unitPricePaise, BigDecimal quantityActual, BigDecimal quantityRequired) {
        BigDecimal quantity = quantityActual != null ? quantityActual
                : (quantityRequired != null ? quantityRequired : BigDecimal.ZERO);
        return BigDecimal.valueOf(unitPricePaise).multiply(quantity)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private Supplier resolveSupplier(Long supplierId, Long tenantId) {
        if (supplierId == null) return null;
        return supplierRepository.findByIdAndTenantId(supplierId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
