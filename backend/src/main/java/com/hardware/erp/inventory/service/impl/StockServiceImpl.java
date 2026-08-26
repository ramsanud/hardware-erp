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
    public PageResponse<StockResponse> search(String search, boolean lowStockOnly, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                stockRepository.search(tenantId, search, lowStockOnly, pageable),
                stockMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public StockResponse get(Long productId) {
        return stockMapper.toResponse(requireStock(productId, SecurityUtils.requireCurrentTenantId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> movements(Long productId, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        requireStock(productId, tenantId);
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

    private Stock createStockRow(Long productId, Long tenantId) {
        Product product = productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        Stock stock = Stock.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .product(product)
                .quantityOnHand(BigDecimal.ZERO)
                .build();
        return stockRepository.save(stock);
    }

    private Stock requireStock(Long productId, Long tenantId) {
        return stockRepository.findByTenantIdAndProductId(tenantId, productId)
                .orElseGet(() -> createStockRow(productId, tenantId));
    }
}
