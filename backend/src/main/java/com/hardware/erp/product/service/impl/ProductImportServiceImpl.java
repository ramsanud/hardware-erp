package com.hardware.erp.product.service.impl;

import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.product.dto.*;
import com.hardware.erp.product.entity.Brand;
import com.hardware.erp.product.entity.Category;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.extraction.ExtractedProductRow;
import com.hardware.erp.product.extraction.ProductDocumentExtractionService;
import com.hardware.erp.product.extraction.ProductExtractionResult;
import com.hardware.erp.product.repository.BrandRepository;
import com.hardware.erp.product.repository.CategoryRepository;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.product.service.ProductImportService;
import com.hardware.erp.product.service.ProductService;
import com.hardware.erp.purchase.upload.DocumentUploadValidation;
import com.hardware.erp.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * See ProductDocumentExtractionService for the pluggable-parser design,
 * mirroring the Purchase module's Supplier Bill Import (spec parity, CR-036).
 * Every row in a product import is treated as a brand-new product - there is
 * no "existing item, add stock" merge concept the way Purchase Import has,
 * since importing products is catalogue setup, not a transaction. A row
 * whose code or name already exists in the catalogue is reported as an
 * error and skipped at confirm, never silently overwritten.
 */
@Service
@RequiredArgsConstructor
public class ProductImportServiceImpl implements ProductImportService {

    private final List<ProductDocumentExtractionService> extractors;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductService productService;

    @Override
    @Transactional(readOnly = true)
    public ProductImportPreviewResponse preview(MultipartFile file) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        String extension = DocumentUploadValidation.validateAndGetExtension(file);

        ProductDocumentExtractionService extractor = extractors.stream()
                .filter(e -> e.supports(extension))
                .findFirst()
                .orElse(null);
        if (extractor == null) {
            return new ProductImportPreviewResponse(false,
                    "Automatic extraction is unavailable for this file type in this version. "
                    + "CSV and Excel (.xlsx) are supported today.",
                    List.of(), List.of(), 0, 0);
        }

        ProductExtractionResult extraction;
        try {
            extraction = extractor.extract(file.getInputStream());
        } catch (Exception e) {
            throw new BusinessException(
                    "Could not read this file - it may be corrupted, password-protected, or not a real "
                    + extension.toUpperCase(Locale.ROOT) + " file despite its name",
                    HttpStatus.UNPROCESSABLE_ENTITY, "EXTRACTION_FAILED");
        }

        if (extraction.rows().isEmpty()) {
            return new ProductImportPreviewResponse(true,
                    "No data rows were found in this file.", List.of(), extraction.warnings(), 0, 0);
        }

        Set<String> seenCodes = new HashSet<>();
        Set<String> seenNames = new HashSet<>();
        List<ProductImportRowPreview> previews = new ArrayList<>();
        int rowsWithErrors = 0;

        for (ExtractedProductRow row : extraction.rows()) {
            List<String> errors = new ArrayList<>(row.getErrors());

            Long categoryId = null;
            if (row.getCategoryName() != null) {
                categoryId = categoryRepository.findByCategoryNameIgnoreCaseAndTenantId(row.getCategoryName(), tenantId)
                        .map(Category::getId).orElse(null);
            }
            Long brandId = null;
            if (row.getBrandName() != null) {
                brandId = brandRepository.findByBrandNameIgnoreCaseAndTenantId(row.getBrandName(), tenantId)
                        .map(Brand::getId).orElse(null);
            }

            if (row.getProductCode() != null) {
                String codeKey = row.getProductCode().trim().toUpperCase(Locale.ROOT);
                if (!seenCodes.add(codeKey)) {
                    errors.add("Product code \"" + row.getProductCode() + "\" is repeated earlier in this file");
                } else if (productRepository.findByTenantIdAndProductCodeIgnoreCase(tenantId, row.getProductCode()).isPresent()) {
                    errors.add("Product code \"" + row.getProductCode() + "\" already exists in the catalogue");
                }
            }
            if (row.getProductName() != null) {
                String nameKey = row.getProductName().trim().toLowerCase(Locale.ROOT);
                if (!seenNames.add(nameKey)) {
                    errors.add("Product name \"" + row.getProductName() + "\" is repeated earlier in this file");
                } else if (productRepository.findByTenantIdAndProductNameIgnoreCase(tenantId, row.getProductName()).isPresent()) {
                    errors.add("Product name \"" + row.getProductName() + "\" already exists in the catalogue");
                }
            }

            if (!errors.isEmpty()) rowsWithErrors++;
            previews.add(new ProductImportRowPreview(
                    row.getRowNumber(), row.getProductName(), row.getProductCode(),
                    row.getCategoryName(), categoryId, row.getBrandName(), brandId,
                    row.getUnit(), row.getHsnCode(), row.getGstRatePercent(),
                    row.getPurchasePriceRupees(), row.getSellingPriceRupees(), row.getMrpRupees(),
                    row.getMinimumStock(), row.getReorderLevel(), errors));
        }

        return new ProductImportPreviewResponse(true, null, previews, extraction.warnings(),
                previews.size(), rowsWithErrors);
    }

    /**
     * One transaction, all-or-nothing - same principle as Purchase Import's
     * own confirm(). Preview already surfaced duplicate code/name and
     * missing-field problems before the owner ever gets here; if a row
     * still fails at this point (a race with another request, or an
     * entitlement limit reached partway through a large batch), the whole
     * batch rolls back rather than leaving a half-imported catalogue with
     * no clear record of which rows actually landed.
     */
    @Override
    @Transactional
    public ProductImportResultResponse confirm(ProductImportConfirmRequest request) {
        int created = 0;
        for (ProductImportConfirmRow row : request.rows()) {
            ProductRequest productRequest = new ProductRequest(
                    blankToNull(row.productCode()), row.productName(), row.categoryId(), row.brandId(),
                    null, null, null, row.unit(), null, blankToNull(row.hsnCode()),
                    row.gstRatePercent(),
                    toPaise(row.purchasePriceRupees()), toPaise(row.sellingPriceRupees()), toPaise(row.mrpRupees()),
                    row.minimumStock() != null ? row.minimumStock() : BigDecimal.ZERO,
                    row.reorderLevel() != null ? row.reorderLevel() : BigDecimal.ZERO,
                    ProductStatus.ACTIVE);
            try {
                productService.create(productRequest);
                created++;
            } catch (Exception e) {
                throw new BusinessException("Row " + row.rowNumber() + " (" + row.productName() + "): " + e.getMessage());
            }
        }
        return new ProductImportResultResponse(created);
    }

    private Long toPaise(BigDecimal rupees) {
        if (rupees == null) return 0L;
        return rupees.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
