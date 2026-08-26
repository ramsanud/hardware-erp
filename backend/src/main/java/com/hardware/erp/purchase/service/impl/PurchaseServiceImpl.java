package com.hardware.erp.purchase.service.impl;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.common.activity.ActivityAction;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.service.StockService;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.purchase.dto.*;
import com.hardware.erp.purchase.entity.Purchase;
import com.hardware.erp.purchase.entity.PurchaseItem;
import com.hardware.erp.purchase.entity.PurchasePayment;
import com.hardware.erp.purchase.entity.PurchaseStatus;
import com.hardware.erp.purchase.mapper.PurchaseMapper;
import com.hardware.erp.purchase.repository.PurchaseDocumentRepository;
import com.hardware.erp.purchase.repository.PurchasePaymentRepository;
import com.hardware.erp.purchase.repository.PurchaseRepository;
import com.hardware.erp.purchase.service.PurchaseService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.supplier.entity.Supplier;
import com.hardware.erp.supplier.repository.SupplierRepository;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Depends on:
 *   Inventory - StockService.applyMovement increases stock on create (PURCHASE_RECEIPT)
 *   and decreases it back on cancel (PURCHASE_RETURN) - never a direct
 *   quantity_on_hand mutation, mirrors InvoiceServiceImpl exactly.
 *   Supplier - an existing supplier row, resolved by id (a purchase is
 *   never "find or create" the way Invoice's walk-in customer is; a
 *   supplier is expected to already exist as a business relationship
 *   before any purchase is recorded against them).
 */
@Service
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    private static final String MODULE = "PURCHASE";
    private static final String ENTITY = "PURCHASE";

    private final PurchaseRepository purchaseRepository;
    private final DocumentSequenceService documentSequenceService;
    private final PurchasePaymentRepository purchasePaymentRepository;
    private final PurchaseDocumentRepository purchaseDocumentRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final TenantRepository tenantRepository;
    private final PurchaseMapper purchaseMapper;
    private final ActivityLogService activityLog;

    @Override
    @Transactional
    public PurchaseResponse create(PurchaseRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();

        Supplier supplier = supplierRepository.findByIdAndTenantId(request.supplierId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", request.supplierId()));

        Purchase purchase = Purchase.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .purchaseNumber(nextPurchaseNumber(tenantId))
                .supplier(supplier)
                .supplierBillNumber(blankToNull(request.supplierBillNumber()))
                .purchaseDate(request.purchaseDate())
                .remarks(request.remarks())
                .status(PurchaseStatus.RECEIVED)
                .build();

        long subtotal = 0L;
        long gstTotal = 0L;
        for (PurchaseItemRequest itemRequest : request.items()) {
            PurchaseItem item = buildLine(itemRequest, tenantId);
            item.setPurchase(purchase);
            purchase.getItems().add(item);
            subtotal += item.getLineSubtotalPaise();
            gstTotal += item.getLineGstPaise();
        }
        long total = subtotal + gstTotal;

        Long initialPayment = request.initialPaymentPaise();
        if (initialPayment != null && initialPayment > total) {
            throw new BusinessException(
                    "Initial payment cannot exceed the purchase total",
                    HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_EXCEEDS_TOTAL");
        }
        if (initialPayment != null && initialPayment > 0 && request.paymentMethod() == null) {
            throw new BusinessException("A payment method is required for the initial payment");
        }

        purchase.setSubtotalPaise(subtotal);
        purchase.setGstAmountPaise(gstTotal);
        purchase.setTotalPaise(total);
        purchase.setPaidPaise(0L);
        purchase.recalculate();

        Purchase saved = purchaseRepository.save(purchase);

        // Stock arrives after the purchase has an id, so the movement's
        // reference_id points at a row that already exists.
        for (PurchaseItem item : saved.getItems()) {
            stockService.applyMovement(item.getProduct().getId(), item.getQuantity(),
                    MovementType.PURCHASE_RECEIPT, "PURCHASE", saved.getId(), null);
            if (request.updateProductCost()) {
                Product product = item.getProduct();
                product.setPurchasePricePaise(item.getUnitPricePaise());
                productRepository.save(product);
            }
        }

        List<PurchasePayment> payments = List.of();
        if (initialPayment != null && initialPayment > 0) {
            PurchasePayment payment = purchasePaymentRepository.save(PurchasePayment.builder()
                    .tenant(saved.getTenant())
                    .purchase(saved)
                    .amountPaise(initialPayment)
                    .paymentMethod(request.paymentMethod())
                    .paymentDate(LocalDateTime.now())
                    .notes("Initial payment at purchase creation")
                    .build());
            payments = List.of(payment);
            saved.setPaidPaise(initialPayment);
            saved.recalculate();
            saved = purchaseRepository.save(saved);
        }

        Map<String, Object> logged = new LinkedHashMap<>();
        logged.put("purchaseNumber", saved.getPurchaseNumber());
        logged.put("supplierName", supplier.getSupplierName());
        logged.put("totalPaise", saved.getTotalPaise());
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getPurchaseNumber(), logged);

        return purchaseMapper.toResponse(saved, payments, false);
    }

    @Override
    @Transactional(readOnly = true)
    public com.hardware.erp.purchase.entity.PurchaseDocument getDocument(Long purchaseId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        require(purchaseId, tenantId); // tenant-ownership check before touching the document row
        return purchaseDocumentRepository.findByPurchaseIdAndTenantId(purchaseId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase document for purchase", purchaseId));
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Purchase purchase = require(id, tenantId);
        boolean hasDocument = purchaseDocumentRepository.findByPurchaseIdAndTenantId(id, tenantId).isPresent();
        return purchaseMapper.toResponse(purchase,
                purchasePaymentRepository.findByPurchaseIdOrderByPaymentDateDesc(id), hasDocument);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PurchaseSummaryResponse> search(String search, PurchaseStatus status, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                purchaseRepository.search(tenantId, search, status, pageable),
                purchaseMapper::toSummary);
    }

    @Override
    @Transactional
    public PurchaseResponse addPayment(Long purchaseId, RecordPurchasePaymentRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Purchase purchase = require(purchaseId, tenantId);

        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw new BusinessException("A cancelled purchase cannot take a payment");
        }
        long newPaid = purchase.getPaidPaise() + request.amountPaise();
        if (newPaid > purchase.getTotalPaise()) {
            throw new BusinessException(
                    "This payment would take the purchase over its total",
                    HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_EXCEEDS_TOTAL");
        }

        PurchasePayment payment = purchasePaymentRepository.save(PurchasePayment.builder()
                .tenant(purchase.getTenant())
                .purchase(purchase)
                .amountPaise(request.amountPaise())
                .paymentMethod(request.paymentMethod())
                .paymentDate(LocalDateTime.now())
                .notes(request.notes())
                .build());

        purchase.setPaidPaise(newPaid);
        purchase.recalculate();
        Purchase saved = purchaseRepository.save(purchase);

        activityLog.action(MODULE, "PAYMENT", payment.getId(), saved.getPurchaseNumber(),
                ActivityAction.CREATE, "Payment recorded, new status " + saved.getStatus());

        boolean hasDocument = purchaseDocumentRepository.findByPurchaseIdAndTenantId(purchaseId, tenantId).isPresent();
        return purchaseMapper.toResponse(saved,
                purchasePaymentRepository.findByPurchaseIdOrderByPaymentDateDesc(purchaseId), hasDocument);
    }

    @Override
    @Transactional
    public PurchaseResponse cancel(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Purchase purchase = require(id, tenantId);

        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw new BusinessException("This purchase is already cancelled");
        }

        for (PurchaseItem item : purchase.getItems()) {
            stockService.applyMovement(item.getProduct().getId(), item.getQuantity().negate(),
                    MovementType.PURCHASE_RETURN, "PURCHASE", purchase.getId(),
                    "Purchase " + purchase.getPurchaseNumber() + " cancelled");
        }

        purchase.setStatus(PurchaseStatus.CANCELLED);
        Purchase saved = purchaseRepository.save(purchase);

        activityLog.deleted(MODULE, ENTITY, saved.getId(), saved.getPurchaseNumber(),
                "Purchase cancelled, stock reversed");

        boolean hasDocument = purchaseDocumentRepository.findByPurchaseIdAndTenantId(id, tenantId).isPresent();
        return purchaseMapper.toResponse(saved,
                purchasePaymentRepository.findByPurchaseIdOrderByPaymentDateDesc(id), hasDocument);
    }

    // ---------------------------------------------------------------

    private Purchase require(Long id, Long tenantId) {
        return purchaseRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase", id));
    }

    private PurchaseItem buildLine(PurchaseItemRequest request, Long tenantId) {
        Product product = productRepository.findByIdAndTenantId(request.productId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));

        BigDecimal lineSubtotal = request.quantity()
                .multiply(BigDecimal.valueOf(request.unitPricePaise()));
        long lineSubtotalPaise = lineSubtotal.setScale(0, RoundingMode.HALF_UP).longValue();
        long lineGstPaise = BigDecimal.valueOf(lineSubtotalPaise)
                .multiply(request.gstRatePercent())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();

        return PurchaseItem.builder()
                .product(product)
                .productNameSnapshot(product.getProductName())
                .quantity(request.quantity())
                .unit(product.getUnit())
                .unitPricePaise(request.unitPricePaise())
                .gstRatePercent(request.gstRatePercent())
                .lineSubtotalPaise(lineSubtotalPaise)
                .lineGstPaise(lineGstPaise)
                .lineTotalPaise(lineSubtotalPaise + lineGstPaise)
                .build();
    }

    /** Generates PUR-000001, PUR-000002 ... - same pattern as SupplierServiceImpl.resolveCode()/InvoiceServiceImpl.nextInvoiceNumber(). */
    private String nextPurchaseNumber(Long tenantId) {
        return documentSequenceService.next(DocumentType.PURCHASE, tenantId);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
