package com.hardware.erp.inventory.service.impl;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.inventory.dto.StockAdjustmentRequest;
import com.hardware.erp.inventory.dto.StockMovementResponse;
import com.hardware.erp.inventory.dto.StockResponse;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.entity.Stock;
import com.hardware.erp.inventory.entity.StockMovement;
import com.hardware.erp.inventory.mapper.StockMapper;
import com.hardware.erp.inventory.repository.StockMovementRepository;
import com.hardware.erp.inventory.repository.StockRepository;
import com.hardware.erp.inventory.service.StockService;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Single location per shop for now (CR-021) - one Stock row per (tenant,
 * product), created lazily on first access rather than by ProductService,
 * so Inventory stays a separate module that Product does not need to know
 * exists.
 */
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private final StockRepository stockRepository;
    private final StockMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final StockMapper stockMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockResponse> search(String search, boolean lowStockOnly,
                                              boolean outOfStockOnly, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                stockRepository.search(tenantId, search, lowStockOnly, outOfStockOnly, pageable),
                stockMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public StockResponse get(Long productId) {
        return stockMapper.toResponse(readStock(productId, SecurityUtils.requireCurrentTenantId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> movements(Long productId, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        // Only to 404 on a product this tenant does not own - a product with no
        // stock row simply has no movements, and the query below returns empty.
        readStock(productId, tenantId);
        return PageResponse.from(
                movementRepository.findByProduct(tenantId, productId, pageable),
                stockMapper::toResponse);
    }

    @Override
    @Transactional
    public StockMovementResponse adjust(Long productId, StockAdjustmentRequest request) {
        if (request.quantityChange().compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("Adjustment quantity cannot be zero");
        }
        StockMovement movement = applyMovement(productId, request.quantityChange(),
                MovementType.ADJUSTMENT, "MANUAL", null, request.notes());
        return stockMapper.toResponse(movement);
    }

    @Override
    @Transactional
    public StockMovement applyMovement(Long productId, BigDecimal quantityChange, MovementType type,
                                       String referenceType, Long referenceId, String notes) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Stock stock = stockRepository.lockByTenantIdAndProductId(tenantId, productId)
                .orElseGet(() -> createStockRow(productId, tenantId));

        BigDecimal newBalance = stock.getQuantityOnHand().add(quantityChange);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(
                    "Not enough stock of " + stock.getProduct().getProductName()
                            + ": " + stock.getQuantityOnHand().stripTrailingZeros().toPlainString()
                            + " on hand, " + quantityChange.abs().stripTrailingZeros().toPlainString()
                            + " requested",
                    HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_STOCK");
        }
        stock.setQuantityOnHand(newBalance);
        stockRepository.save(stock);

        return movementRepository.save(StockMovement.builder()
                .tenant(stock.getTenant())
                .product(stock.getProduct())
                .movementType(type)
                .quantityChange(quantityChange)
                .balanceAfter(newBalance)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .notes(notes)
                .build());
    }

    @Override
    @Transactional
    public StockMovement applyPurchaseReceipt(Long productId, BigDecimal quantityChange, Long unitCostPaise,
                                              String referenceType, Long referenceId, String notes) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Stock stock = stockRepository.lockByTenantIdAndProductId(tenantId, productId)
                .orElseGet(() -> createStockRow(productId, tenantId));

        BigDecimal oldQty = stock.getQuantityOnHand();
        BigDecimal newBalance = oldQty.add(quantityChange);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(
                    "Not enough stock of " + stock.getProduct().getProductName()
                            + ": " + oldQty.stripTrailingZeros().toPlainString()
                            + " on hand, " + quantityChange.abs().stripTrailingZeros().toPlainString()
                            + " requested",
                    HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_STOCK");
        }

        // Weighted average, only moved by a real receipt with a real cost.
        // A purchase return (a negative quantityChange, no cost supplied by
        // its caller) leaves the average as it stands: the goods that leave
        // were already costed at whatever the average was when they arrived.
        Long currentAverage = stock.getAverageCostPaise();
        long oldAverage = currentAverage == null ? 0L : currentAverage;
        if (unitCostPaise != null && quantityChange.signum() > 0) {
            if (oldQty.signum() <= 0 || oldAverage <= 0) {
                stock.setAverageCostPaise(unitCostPaise);
            } else {
                BigDecimal oldValue = oldQty.multiply(BigDecimal.valueOf(oldAverage));
                BigDecimal newValue = quantityChange.multiply(BigDecimal.valueOf(unitCostPaise));
                stock.setAverageCostPaise(oldValue.add(newValue)
                        .divide(newBalance, 0, java.math.RoundingMode.HALF_UP)
                        .longValueExact());
            }
        }

        stock.setQuantityOnHand(newBalance);
        stockRepository.save(stock);

        return movementRepository.save(StockMovement.builder()
                .tenant(stock.getTenant())
                .product(stock.getProduct())
                .movementType(quantityChange.signum() > 0 ? MovementType.PURCHASE_RECEIPT : MovementType.PURCHASE_RETURN)
                .quantityChange(quantityChange)
                .balanceAfter(newBalance)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .notes(notes)
                .build());
    }

    private Stock createStockRow(Long productId, Long tenantId) {
        return stockRepository.save(zeroStock(productId, tenantId));
    }

    /**
     * BUG-BE-003: the read path must NOT create the row.
     *
     * "Created lazily on first access" (see the class comment) was implemented
     * as a save() reached from get() and movements(), both of which are
     * {@code @Transactional(readOnly = true)}. PostgreSQL refuses an INSERT in
     * a read-only transaction outright, so every product without a stock row -
     * 10,004 of 10,018 on the dev database, and *every* product until its first
     * movement - answered 500 instead of "0 on hand".
     *
     * A GET must not have side effects, so the zero row is built and mapped
     * without ever being persisted. The write path (applyMovement) still
     * creates it for real, which is the only place that legitimately can.
     */
    private Stock readStock(Long productId, Long tenantId) {
        return stockRepository.findByTenantIdAndProductId(tenantId, productId)
                .orElseGet(() -> zeroStock(productId, tenantId));
    }

    /**
     * Unsaved. StockMapper reads only the product and the quantity, so a
     * transient instance maps to exactly the response a freshly-created row
     * would have produced.
     */
    private Stock zeroStock(Long productId, Long tenantId) {
        Product product = productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        return Stock.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .product(product)
                .quantityOnHand(BigDecimal.ZERO)
                .build();
    }
}
