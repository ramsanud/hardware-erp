package com.hardware.erp.purchase.service.impl;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.entity.Stock;
import com.hardware.erp.inventory.repository.StockRepository;
import com.hardware.erp.inventory.service.StockService;
import com.hardware.erp.product.dto.ProductRequest;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.repository.BrandRepository;
import com.hardware.erp.product.repository.CategoryRepository;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.product.service.ProductService;
import com.hardware.erp.purchase.dto.*;
import com.hardware.erp.purchase.entity.*;
import com.hardware.erp.purchase.extraction.DocumentExtractionService;
import com.hardware.erp.purchase.extraction.ExtractedRow;
import com.hardware.erp.purchase.extraction.ExtractionResult;
import com.hardware.erp.purchase.mapper.PurchaseMapper;
import com.hardware.erp.purchase.repository.PurchaseDocumentRepository;
import com.hardware.erp.purchase.repository.PurchaseRepository;
import com.hardware.erp.purchase.service.PurchaseImportService;
import com.hardware.erp.purchase.upload.DocumentUploadValidation;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.supplier.entity.Supplier;
import com.hardware.erp.supplier.repository.SupplierRepository;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * See DocumentExtractionService for the pluggable-parser design and
 * ImportConfirmRow for why Supplier/Brand/Category are always pre-
 * resolved ids by the time confirm() runs - only Product creation
 * happens inside this class's own transaction (spec §15).
 */
@Service
@RequiredArgsConstructor
public class PurchaseImportServiceImpl implements PurchaseImportService {

    private final List<DocumentExtractionService> extractors;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final StockRepository stockRepository;
    private final PurchaseRepository purchaseRepository;
    private final DocumentSequenceService documentSequenceService;
    private final PurchaseDocumentRepository purchaseDocumentRepository;
    private final ProductService productService;
    private final StockService stockService;
    private final TenantRepository tenantRepository;
    private final PurchaseMapper purchaseMapper;
    private final ActivityLogService activityLog;

    @Override
    @Transactional(readOnly = true)
    public ImportPreviewResponse preview(MultipartFile file) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        String extension = DocumentUploadValidation.validateAndGetExtension(file);

        DocumentExtractionService extractor = extractors.stream()
                .filter(e -> e.supports(extension))
                .findFirst()
                .orElse(null);

        if (extractor == null) {
            return new ImportPreviewResponse(false,
                    "Automatic extraction is unavailable for this file type in this version. "
                    + "CSV and Excel (.xlsx) are supported today; PDF and image/scanned-bill "
                    + "extraction need a configured OCR/AI provider. Enter this purchase "
                    + "manually, or export the bill as CSV/Excel first.",
                    List.of(), List.of(), 0, 0, 0, 0);
        }

        ExtractionResult extraction;
        try {
            extraction = extractor.extract(file.getInputStream());
        } catch (Exception e) {
            throw new BusinessException(
                    "Could not read this file - it may be corrupted, password-protected, or not a real "
                    + extension.toUpperCase() + " file despite its name",
                    HttpStatus.UNPROCESSABLE_ENTITY, "EXTRACTION_FAILED");
        }

        if (extraction.rows().isEmpty()) {
            return new ImportPreviewResponse(true,
                    "No product rows were found in this file. Check that the first row is a header "
                    + "(Product Name, Brand, Category, SKU, Quantity, Unit, Unit Price, GST %) and the "
                    + "rows below it have data.",
                    List.of(), extraction.warnings(), 0, 0, 0, 0);
        }

        List<ImportRowPreview> previews = new ArrayList<>();
        int newProducts = 0;
        int existingProducts = 0;
        int rowsWithErrors = 0;
        for (ExtractedRow row : extraction.rows()) {
            ImportRowPreview preview = matchRow(row, tenantId);
            previews.add(preview);
            if (!preview.errors().isEmpty()) rowsWithErrors++;
            else if (preview.productIsExisting()) existingProducts++;
            else newProducts++;
        }

        return new ImportPreviewResponse(true, null, previews, extraction.warnings(),
                previews.size(), rowsWithErrors, newProducts, existingProducts);
    }

    private ImportRowPreview matchRow(ExtractedRow row, Long tenantId) {
        List<String> errors = new ArrayList<>(row.getErrors());

        Optional<Product> matchedProduct = Optional.empty();
        if (row.getSku() != null) {
            matchedProduct = productRepository.findByTenantIdAndProductCodeIgnoreCase(tenantId, row.getSku());
        }
        if (matchedProduct.isEmpty() && row.getProductName() != null) {
            matchedProduct = productRepository.findByTenantIdAndProductNameIgnoreCase(tenantId, row.getProductName());
        }

        Optional<com.hardware.erp.product.entity.Brand> matchedBrand = row.getBrandName() != null
                ? brandRepository.findByBrandNameIgnoreCaseAndTenantId(row.getBrandName(), tenantId) : Optional.empty();
        Optional<com.hardware.erp.product.entity.Category> matchedCategory = row.getCategoryName() != null
                ? categoryRepository.findByCategoryNameIgnoreCaseAndTenantId(row.getCategoryName(), tenantId) : Optional.empty();

        BigDecimal quantity = row.getQuantity() != null ? row.getQuantity() : BigDecimal.ZERO;
        Long unitPricePaise = row.getUnitPriceRupees() != null
                ? row.getUnitPriceRupees().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue()
                : null;
        BigDecimal gstRate = row.getGstRatePercent();
        Long lineTotal = unitPricePaise != null
                ? quantity.multiply(BigDecimal.valueOf(unitPricePaise))
                    .multiply(BigDecimal.ONE.add(gstRate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(0, RoundingMode.HALF_UP).longValue()
                : null;

        if (matchedProduct.isEmpty() && row.getUnit() == null && errors.isEmpty()) {
            errors.add("Unit is required for a new product");
        }
        if (matchedProduct.isEmpty() && row.getCategoryName() == null && matchedCategory.isEmpty()) {
            // Category is optional on Product itself (nullable FK) - not an error, just noted implicitly by categoryName being null in the response.
        }

        BigDecimal currentStock = matchedProduct
                .flatMap(p -> stockRepository.findByTenantIdAndProductId(tenantId, p.getId()))
                .map(Stock::getQuantityOnHand)
                .orElse(null);

        return new ImportRowPreview(
                row.getRowNumber(), row.getProductName(), row.getBrandName(), row.getCategoryName(), row.getSku(),
                row.getQuantity(), row.getUnit(), unitPricePaise, gstRate, lineTotal,
                matchedProduct.isPresent(), matchedProduct.map(Product::getId).orElse(null),
                matchedProduct.map(Product::getProductName).orElse(null),
                currentStock, matchedProduct.map(Product::getPurchasePricePaise).orElse(null),
                matchedBrand.isPresent(), matchedBrand.map(com.hardware.erp.product.entity.Brand::getId).orElse(null),
                matchedCategory.isPresent(), matchedCategory.map(com.hardware.erp.product.entity.Category::getId).orElse(null),
                errors);
    }

    @Override
    @Transactional
    public ImportResultResponse confirm(ImportConfirmRequest request, MultipartFile file) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Long actorId = SecurityUtils.currentUserId().orElse(null);
        String extension = DocumentUploadValidation.validateAndGetExtension(file);

        Supplier supplier = supplierRepository.findByIdAndTenantId(request.supplierId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", request.supplierId()));

        Long roughTotal = request.rows().stream()
                .mapToLong(r -> lineTotalPaiseEstimate(r.quantity(), r.unitPricePaise(), r.gstRatePercent()))
                .sum();

        if (!request.confirmDuplicateAnyway()) {
            List<Purchase> duplicates = purchaseRepository.findPossibleDuplicates(
                    tenantId, supplier.getId(), blankToNull(request.supplierBillNumber()),
                    request.purchaseDate(), roughTotal);
            if (!duplicates.isEmpty()) {
                Purchase existing = duplicates.get(0);
                throw new BusinessException(
                        "A possible duplicate bill already exists: " + existing.getPurchaseNumber()
                        + " from " + existing.getPurchaseDate() + " for " + IndianCurrencyFormat.rupees(existing.getTotalPaise())
                        + ". Review it, or confirm again to import anyway.",
                        HttpStatus.CONFLICT, "DUPLICATE_BILL_SUSPECTED");
            }
        }

        Purchase purchase = Purchase.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .purchaseNumber(nextPurchaseNumber(tenantId))
                .supplier(supplier)
                .supplierBillNumber(blankToNull(request.supplierBillNumber()))
                .purchaseDate(request.purchaseDate())
                .status(PurchaseStatus.RECEIVED)
                .importedAt(LocalDateTime.now())
                .importedBy(actorId)
                .build();

        long subtotal = 0L;
        long gstTotal = 0L;
        int newProductsCreated = 0;
        int existingProductsMatched = 0;
        int rowsMergedWithEarlierRow = 0;
        BigDecimal stockAdded = BigDecimal.ZERO;
        // Two rows in the same bill can both be "new" but identify the
        // identical product - either the same name (the same fresh item
        // split across two lines at different rates) or, since new
        // products now take the bill's own SKU as their code (see
        // ImportConfirmRow), the same SKU under two slightly different
        // name spellings. Found live, testing a real 100-row file: the
        // second row's create() call failed outright because the first
        // row's create() *within this same transaction* had already
        // claimed that name/code. Reusing the just-created row here means
        // a repeat becomes a second purchase line against one real
        // product, not a hard failure or a silent duplicate. SKU is
        // checked first since it's the stronger identity signal - a real
        // part number should never be split across two products just
        // because a name was typed slightly differently on two lines.
        Map<String, Product> newlyCreatedBySku = new HashMap<>();
        Map<String, Product> newlyCreatedByName = new HashMap<>();

        for (ImportConfirmRow row : request.rows()) {
            Product product;
            if (row.existingProductId() != null) {
                product = productRepository.findByIdAndTenantId(row.existingProductId(), tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Product", row.existingProductId()));
                if (row.updateExistingProductCost()) {
                    product.setPurchasePricePaise(row.unitPricePaise());
                    product = productRepository.save(product);
                }
                existingProductsMatched++;
            } else {
                String skuKey = blankToNull(row.newProductSku()) == null ? null : row.newProductSku().trim().toUpperCase(java.util.Locale.ROOT);
                String nameKey = row.newProductName() == null ? null : row.newProductName().trim().toLowerCase(java.util.Locale.ROOT);
                Product alreadyCreated = skuKey != null ? newlyCreatedBySku.get(skuKey) : null;
                if (alreadyCreated == null && nameKey != null) alreadyCreated = newlyCreatedByName.get(nameKey);

                if (alreadyCreated != null) {
                    product = alreadyCreated;
                    rowsMergedWithEarlierRow++;
                } else {
                    product = createProductForImport(row, tenantId);
                    if (skuKey != null) newlyCreatedBySku.put(skuKey, product);
                    if (nameKey != null) newlyCreatedByName.put(nameKey, product);
                    newProductsCreated++;
                }
            }

            PurchaseItem item = PurchaseItem.builder()
                    .product(product)
                    .productNameSnapshot(product.getProductName())
                    .quantity(row.quantity())
                    .unit(product.getUnit())
                    .unitPricePaise(row.unitPricePaise())
                    .gstRatePercent(row.gstRatePercent())
                    .build();
            long lineSubtotal = row.quantity().multiply(BigDecimal.valueOf(row.unitPricePaise()))
                    .setScale(0, RoundingMode.HALF_UP).longValue();
            long lineGst = BigDecimal.valueOf(lineSubtotal).multiply(row.gstRatePercent())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValue();
            item.setLineSubtotalPaise(lineSubtotal);
            item.setLineGstPaise(lineGst);
            item.setLineTotalPaise(lineSubtotal + lineGst);
            item.setPurchase(purchase);
            purchase.getItems().add(item);

            subtotal += lineSubtotal;
            gstTotal += lineGst;
            stockAdded = stockAdded.add(row.quantity());
        }

        purchase.setSubtotalPaise(subtotal);
        purchase.setGstAmountPaise(gstTotal);
        purchase.setTotalPaise(subtotal + gstTotal);
        purchase.setPaidPaise(0L);
        purchase.recalculate();

        Purchase saved = purchaseRepository.save(purchase);

        for (PurchaseItem item : saved.getItems()) {
            stockService.applyMovement(item.getProduct().getId(), item.getQuantity(),
                    MovementType.PURCHASE_RECEIPT, "PURCHASE", saved.getId(),
                    "Imported from " + file.getOriginalFilename());
        }

        try {
            byte[] bytes = file.getBytes();
            purchaseDocumentRepository.save(PurchaseDocument.builder()
                    .tenant(saved.getTenant())
                    .purchase(saved)
                    .originalFilename(file.getOriginalFilename())
                    .contentType(DocumentUploadValidation.safeContentType(extension))
                    .fileSize((int) file.getSize())
                    .checksumSha256(sha256(bytes))
                    .fileData(bytes)
                    .sourceRowCount(request.rows().size())
                    .uploadedBy(actorId)
                    .uploadedAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            throw new BusinessException("Could not store the uploaded file");
        }

        java.util.Map<String, Object> logged = new java.util.LinkedHashMap<>();
        logged.put("purchaseNumber", saved.getPurchaseNumber());
        logged.put("supplierName", supplier.getSupplierName());
        logged.put("sourceFile", file.getOriginalFilename());
        logged.put("rowsImported", request.rows().size());
        logged.put("newProductsCreated", newProductsCreated);
        logged.put("existingProductsMatched", existingProductsMatched);
        logged.put("rowsMergedWithEarlierRow", rowsMergedWithEarlierRow);
        activityLog.created("PURCHASE", "PURCHASE_IMPORT", saved.getId(), saved.getPurchaseNumber(), logged);

        return new ImportResultResponse(
                saved.getId(), saved.getPurchaseNumber(), request.rows().size(),
                existingProductsMatched, newProductsCreated, rowsMergedWithEarlierRow,
                stockAdded.stripTrailingZeros().toPlainString(),
                IndianCurrencyFormat.rupees(saved.getTotalPaise()));
    }

    /** Reuses the existing, already-tested Product creation path (code generation, entitlement limits, GST validation) rather than duplicating it - see ImportConfirmRow's own note on why this is the one exception allowed to write inside this transaction. */
    private Product createProductForImport(ImportConfirmRow row, Long tenantId) {
        ProductRequest request = new ProductRequest(
                row.newProductSku(), row.newProductName(), row.newProductCategoryId(), row.newProductBrandId(),
                null, null, null, row.newProductUnit(), null, null,
                row.gstRatePercent(), row.unitPricePaise(), row.unitPricePaise(), row.unitPricePaise(),
                BigDecimal.ZERO, BigDecimal.ZERO, ProductStatus.ACTIVE, null, null);
        var created = productService.create(request);
        return productRepository.findByIdAndTenantId(created.id(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", created.id()));
    }

    private long lineTotalPaiseEstimate(BigDecimal quantity, Long unitPricePaise, BigDecimal gstRatePercent) {
        long subtotal = quantity.multiply(BigDecimal.valueOf(unitPricePaise)).setScale(0, RoundingMode.HALF_UP).longValue();
        long gst = BigDecimal.valueOf(subtotal).multiply(gstRatePercent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValue();
        return subtotal + gst;
    }

    private String nextPurchaseNumber(Long tenantId) {
        return documentSequenceService.next(DocumentType.PURCHASE, tenantId);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available on the JVM", e);
        }
    }
}
