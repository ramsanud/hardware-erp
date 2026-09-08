package com.hardware.erp.project.service.impl;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.service.StockService;
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
 * entity for why supplier is nullable), Inventory Module (CR-064).
 *
 * CR-064 - stock. Until that CR this service wrote project_material and
 * nothing else, so every unit a project consumed stayed on the shelf as far
 * as stock.quantity_on_hand was concerned.
 *
 * Three rules govern every stock call below, and none of them is local to
 * this class:
 *
 *   1. quantity_actual alone moves stock. quantity_required and
 *      quantity_estimated are planning figures, and quantity_actual is
 *      nullable precisely because nothing has physically left the godown
 *      until someone records that it has. quantity_wastage is a breakdown
 *      WITHIN the actual figure, not additional material - the same reading
 *      totalCost() below already takes by pricing actual and ignoring
 *      wastage. If that reading ever changes, both have to change together.
 *
 *   2. Corrections are compensating movements, never rewritten history.
 *      15 -> 20 takes a further 5 out; 20 -> 15 puts 5 back. This mirrors
 *      InvoiceServiceImpl's amend path exactly.
 *
 *   3. StockService.applyMovement is the only way stock moves. It locks the
 *      row, refuses to drive a balance negative, takes the tenant from the
 *      JWT and joins this transaction - so a material row and its movement
 *      commit or roll back together. There is no second stock calculation
 *      here and there must never be one.
 */
@Service
@RequiredArgsConstructor
public class ProjectMaterialServiceImpl implements ProjectMaterialService {

    private static final String MODULE = "PROJECT";
    private static final String ENTITY = "PROJECT_MATERIAL";
    /** Ties every movement back to the material row - see V53's index. */
    private static final String REFERENCE_TYPE = "PROJECT_MATERIAL";

    private final ProjectMaterialRepository materialRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final ProjectServiceImpl projectService;
    private final StockService stockService;
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

        // After the save, so reference_id points at a row that already
        // exists - the rule InvoiceServiceImpl documents for the same reason.
        BigDecimal consumed = consumedQuantity(saved.getQuantityActual());
        if (consumed.signum() > 0) {
            stockService.applyMovement(product.getId(), consumed.negate(),
                    MovementType.PROJECT_CONSUMPTION, REFERENCE_TYPE, saved.getId(),
                    "Consumed by project " + project.getProjectName());
        }

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

        // Read what this row currently claims BEFORE any setter runs - these
        // two are the whole basis of the correction below, and the setters
        // destroy them.
        Long previousProductId = material.getProduct().getId();
        BigDecimal previousConsumed = consumedQuantity(material.getQuantityActual());

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

        ProjectMaterial saved = materialRepository.save(material);
        applyConsumptionChange(previousProductId, previousConsumed,
                product.getId(), consumedQuantity(saved.getQuantityActual()),
                saved.getId(), "Project material " + saved.getId() + " amended");

        return mapper.toResponse(saved);
    }

    /**
     * Moves stock from "the row used to say previousConsumed of
     * previousProductId" to "it now says nowConsumed of nowProductId".
     *
     * Two genuinely different cases, and collapsing them is how this goes
     * wrong. When the product is unchanged only the DIFFERENCE moves -
     * 15 -> 20 takes a further 5, not another 20. When the product changed
     * there is no meaningful difference to take, so the old product is made
     * whole and the new one is consumed in full.
     *
     * The reversal is issued FIRST in that second case, deliberately: it puts
     * stock back before the new line asks for any, so swapping a material for
     * one the shop is short of does not fail on stock the same edit was about
     * to release.
     */
    private void applyConsumptionChange(Long previousProductId, BigDecimal previousConsumed,
                                        Long nowProductId, BigDecimal nowConsumed,
                                        Long materialId, String notes) {
        if (previousProductId.equals(nowProductId)) {
            BigDecimal delta = nowConsumed.subtract(previousConsumed);
            if (delta.signum() == 0) return;
            // Stock moves opposite to consumption: using more takes stock out.
            stockService.applyMovement(nowProductId, delta.negate(),
                    delta.signum() > 0 ? MovementType.PROJECT_CONSUMPTION
                                       : MovementType.PROJECT_CONSUMPTION_REVERSAL,
                    REFERENCE_TYPE, materialId, notes);
            return;
        }

        if (previousConsumed.signum() > 0) {
            stockService.applyMovement(previousProductId, previousConsumed,
                    MovementType.PROJECT_CONSUMPTION_REVERSAL, REFERENCE_TYPE, materialId,
                    notes + " - replaced by a different product");
        }
        if (nowConsumed.signum() > 0) {
            stockService.applyMovement(nowProductId, nowConsumed.negate(),
                    MovementType.PROJECT_CONSUMPTION, REFERENCE_TYPE, materialId, notes);
        }
    }

    /**
     * The quantity that has physically left the godown for this row.
     *
     * Null means the material is still only planned. A negative actual is
     * refused rather than silently clamped: it would otherwise ADD stock
     * through a consumption path, which is a way to invent inventory. The
     * DTO carries no @PositiveOrZero, so this is the only guard.
     */
    private BigDecimal consumedQuantity(BigDecimal quantityActual) {
        if (quantityActual == null) return BigDecimal.ZERO;
        if (quantityActual.signum() < 0) {
            throw new BusinessException("Actual quantity used cannot be negative");
        }
        return quantityActual;
    }

    @Override
    @Transactional
    public void remove(Long projectId, Long materialId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        projectService.require(projectId, tenantId);
        ProjectMaterial material = materialRepository.findByIdAndProjectIdAndTenantId(materialId, projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project material", materialId));
        // Removing the row is a correction of a record, so whatever it claimed
        // was consumed goes back. This is the one reversal path that exists:
        // cancelling the PROJECT deliberately does not restore stock, because
        // materials already used on a half-built job have not come back. See
        // CR-064 for that decision.
        BigDecimal consumed = consumedQuantity(material.getQuantityActual());
        Long productId = material.getProduct().getId();
        String productName = material.getProduct().getProductName();

        materialRepository.delete(material);

        if (consumed.signum() > 0) {
            stockService.applyMovement(productId, consumed,
                    MovementType.PROJECT_CONSUMPTION_REVERSAL, REFERENCE_TYPE, materialId,
                    "Project material removed from project " + projectId);
        }

        activityLog.deleted(MODULE, ENTITY, materialId, productName,
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
