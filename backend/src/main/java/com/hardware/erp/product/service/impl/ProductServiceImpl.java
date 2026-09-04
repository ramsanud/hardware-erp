package com.hardware.erp.product.service.impl;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.auth.entity.PermissionCode;
import com.hardware.erp.common.activity.ActivityAction;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import com.hardware.erp.product.dto.ProductDeletedResponse;
import com.hardware.erp.product.dto.ProductPriceHistoryResponse;
import com.hardware.erp.product.dto.ProductRequest;
import com.hardware.erp.product.dto.ProductResponse;
import com.hardware.erp.product.dto.ProductSummaryResponse;
import com.hardware.erp.product.entity.Brand;
import com.hardware.erp.product.entity.Category;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.mapper.ProductMapper;
import com.hardware.erp.product.repository.BrandRepository;
import com.hardware.erp.product.repository.CategoryRepository;
import com.hardware.erp.product.repository.ProductImageRepository;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.product.service.ProductService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.service.EntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Depends On:
 *   Module 1 - PRODUCT_VIEW, PRODUCT_MANAGE and PRODUCT_VIEW_COST, already
 *   seeded by V1 (PermissionCode.java). Category and Brand reuse the same
 *   two view/manage permissions rather than a permission per sub-resource.
 *
 * Purchase, Inventory and Invoice (not yet built) will read products by id.
 * Do not add hard deletion here: those modules will reference product_id
 * permanently, exactly like Supplier.
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String MODULE = "PRODUCT";
    private static final String ENTITY = "PRODUCT";
    private static final int PRICE_HISTORY_LIMIT = 20;

    private final ProductRepository productRepository;
    private final DocumentSequenceService documentSequenceService;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductMapper productMapper;
    private final ActivityLogService activityLog;
    private final TenantRepository tenantRepository;
    private final EntitlementService entitlementService;
    private final com.hardware.erp.invoice.repository.InvoiceItemRepository invoiceItemRepository;

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        entitlementService.requireCanAddProduct();
        String code = resolveCode(request.productCode(), tenantId);
        String name = request.productName().trim();
        String barcode = blankToNull(request.barcode());

        if (productRepository.existsByProductCodeAndTenantId(code, tenantId)) {
            throw new DuplicateResourceException("Product code", code);
        }
        if (productRepository.existsByProductNameIgnoreCaseAndTenantId(name, tenantId)) {
            throw new DuplicateResourceException("Product name", name);
        }
        if (barcode != null && productRepository.existsByBarcodeAndTenantId(barcode, tenantId)) {
            throw new DuplicateResourceException("Barcode", barcode);
        }
        validateAltUnit(request);

        Product product = Product.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .productCode(code)
                .productName(name)
                .category(resolveCategory(request.categoryId(), tenantId))
                .brand(resolveBrand(request.brandId(), tenantId))
                .modelNo(blankToNull(request.modelNo()))
                .manufacturerCode(blankToNull(request.manufacturerCode()))
                .barcode(barcode)
                .unit(request.unit().trim())
                .description(blankToNull(request.description()))
                .hsnCode(blankToNull(request.hsnCode()))
                .gstRatePercent(request.gstRatePercent())
                .purchasePricePaise(request.purchasePricePaise())
                .sellingPricePaise(request.sellingPricePaise())
                .mrpPaise(request.mrpPaise())
                .minimumStock(request.minimumStock())
                .reorderLevel(request.reorderLevel())
                .status(request.status())
                .altUnitLabel(blankToNull(request.altUnitLabel()))
                .altUnitConversionFactor(request.altUnitConversionFactor())
                .build();

        Product saved = productRepository.save(product);
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getProductName(), snapshot(saved));
        return productMapper.toResponse(saved, canViewCost(), false);
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Product product = require(id, tenantId);
        Map<String, Object> before = snapshot(product);
        String name = request.productName().trim();
        String barcode = blankToNull(request.barcode());

        if (request.productCode() != null && !request.productCode().isBlank()
                && productRepository.existsByProductCodeAndTenantIdAndIdNot(
                        request.productCode(), tenantId, id)) {
            throw new DuplicateResourceException("Product code", request.productCode());
        }
        if (productRepository.existsByProductNameIgnoreCaseAndTenantIdAndIdNot(name, tenantId, id)) {
            throw new DuplicateResourceException("Product name", name);
        }
        if (barcode != null
                && productRepository.existsByBarcodeAndTenantIdAndIdNot(barcode, tenantId, id)) {
            throw new DuplicateResourceException("Barcode", barcode);
        }
        validateAltUnit(request);

        if (request.productCode() != null && !request.productCode().isBlank()) {
            product.setProductCode(request.productCode().trim());
        }
        product.setProductName(name);
        product.setCategory(resolveCategory(request.categoryId(), tenantId));
        product.setBrand(resolveBrand(request.brandId(), tenantId));
        product.setModelNo(blankToNull(request.modelNo()));
        product.setManufacturerCode(blankToNull(request.manufacturerCode()));
        product.setBarcode(barcode);
        product.setUnit(request.unit().trim());
        product.setDescription(blankToNull(request.description()));
        product.setHsnCode(blankToNull(request.hsnCode()));
        product.setGstRatePercent(request.gstRatePercent());
        product.setPurchasePricePaise(request.purchasePricePaise());
        product.setSellingPricePaise(request.sellingPricePaise());
        product.setMrpPaise(request.mrpPaise());
        product.setMinimumStock(request.minimumStock());
        product.setReorderLevel(request.reorderLevel());
        product.setStatus(request.status());
        product.setAltUnitLabel(blankToNull(request.altUnitLabel()));
        product.setAltUnitConversionFactor(request.altUnitConversionFactor());

        Product saved = productRepository.save(product);
        activityLog.updated(MODULE, ENTITY, id, saved.getProductName(), before, snapshot(saved));
        return productMapper.toResponse(saved, canViewCost(), productImageRepository.existsById(saved.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Product product = require(id, tenantId);
        return productMapper.toResponse(product, canViewCost(), productImageRepository.existsById(product.getId()));
    }

    /**
     * CR-053 backlog item 1. Depends on Invoice, the one place this module
     * reads across that boundary - a product's own price fields are always
     * the *current* price (see this class's own header comment); this is
     * what it actually sold for on each past sale. Cancelled invoices are
     * excluded - a cancelled sale never really happened at that price.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProductPriceHistoryResponse> priceHistory(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        require(id, tenantId);
        return invoiceItemRepository.findRecentForProduct(id, tenantId, InvoiceStatus.CANCELLED,
                        PageRequest.of(0, PRICE_HISTORY_LIMIT))
                .stream()
                .map(item -> new ProductPriceHistoryResponse(
                        item.getInvoice().getInvoiceDate(),
                        item.getInvoice().getInvoiceNumber(),
                        item.getInvoice().getCustomer().getCustomerName(),
                        item.getQuantity(),
                        productMapper.rupees(item.getUnitPricePaise())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> search(String search, ProductStatus status,
                                                        Long categoryId, Long brandId,
                                                        Pageable pageable) {
        Page<Product> page = productRepository.search(SecurityUtils.requireCurrentTenantId(),
                blankToNull(search), status, categoryId, brandId, pageable);
        Set<Long> withImage = Set.copyOf(productImageRepository.findProductIdsWithImage(
                page.getContent().stream().map(Product::getId).collect(Collectors.toSet())));
        return PageResponse.from(page, product -> productMapper.toSummary(product, withImage.contains(product.getId())));
    }

    @Override
    @Transactional
    public void softDelete(Long id, Long actingUserId) {
        Product product = require(id, SecurityUtils.requireCurrentTenantId());
        product.softDelete(actingUserId);
        productRepository.save(product);
        activityLog.deleted(MODULE, ENTITY, id, product.getProductName(),
                "Deactivated. Purchase and sales history is retained.");
    }

    /**
     * CR-058. The only read path that can see a soft-deleted product -
     * this tenant's deleted rows, a reduced projection, PRODUCT_MANAGE only.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProductDeletedResponse> listDeleted() {
        return productRepository.findDeletedByTenantId(SecurityUtils.requireCurrentTenantId())
                .stream()
                .map(productMapper::toDeletedResponse)
                .toList();
    }

    /**
     * CR-058, the inverse of softDelete - see SupplierServiceImpl.restore for
     * the full reasoning. The guarded UPDATE is the authorisation check; the
     * row is mutated in place, so the product keeps its id, its code, its
     * stock row and every invoice line that references it.
     */
    @Override
    @Transactional
    public void restore(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        if (productRepository.restoreDeleted(id, tenantId) == 0) {
            throw new ResourceNotFoundException("Product", id);
        }
        Product restored = require(id, tenantId);
        activityLog.action(MODULE, ENTITY, id, restored.getProductName(),
                ActivityAction.RESTORE, "Restored from deleted records");
    }

    // ---------------- helpers ----------------

    private boolean canViewCost() {
        return SecurityUtils.currentUser()
                .map(user -> user.hasPermission(PermissionCode.PRODUCT_VIEW_COST))
                .orElse(false);
    }

    private Category resolveCategory(Long categoryId, Long tenantId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    private Brand resolveBrand(Long brandId, Long tenantId) {
        if (brandId == null) {
            return null;
        }
        return brandRepository.findByIdAndTenantId(brandId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", brandId));
    }

    /**
     * Purchase price is deliberately excluded - ActivityLogServiceImpl would
     * redact it anyway, and margin is exactly the data PRODUCT_VIEW_COST
     * exists to protect (PROJECT_SKILLS #28's bank-account lesson, applied
     * to cost price instead).
     */
    private Map<String, Object> snapshot(Product product) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("productCode", product.getProductCode());
        values.put("productName", product.getProductName());
        values.put("categoryId", product.getCategory() == null ? null : product.getCategory().getId());
        values.put("brandId", product.getBrand() == null ? null : product.getBrand().getId());
        values.put("barcode", product.getBarcode());
        values.put("sellingPricePaise", product.getSellingPricePaise());
        values.put("status", product.getStatus() == null ? null : product.getStatus().name());
        return values;
    }

    private Product require(Long id, Long tenantId) {
        return productRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private String resolveCode(String requested, Long tenantId) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim().toUpperCase();
        }
        return documentSequenceService.next(DocumentType.PRODUCT, tenantId);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * CR-053 backlog item 1. Both set or both blank/null - a label with no
     * factor (or vice versa) is exactly the "12 PCS = 0 BOX" nonsense the
     * V40 migration comment warns against, so it is rejected here rather
     * than silently stored half-complete.
     */
    private void validateAltUnit(ProductRequest request) {
        boolean hasLabel = request.altUnitLabel() != null && !request.altUnitLabel().isBlank();
        boolean hasFactor = request.altUnitConversionFactor() != null;
        if (hasLabel != hasFactor) {
            throw new BusinessException(
                    "Alternate unit label and conversion factor must be set together, or not at all");
        }
    }
}
