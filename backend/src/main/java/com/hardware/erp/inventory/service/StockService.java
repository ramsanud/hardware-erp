package com.hardware.erp.inventory.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.inventory.dto.StockAdjustmentRequest;
import com.hardware.erp.inventory.dto.StockMovementResponse;
import com.hardware.erp.inventory.dto.StockResponse;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.entity.StockMovement;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface StockService {

    /**
     * CR-070 added outOfStockOnly. The two flags AND: setting both returns
     * rows that are both, which for any non-negative reorder level is exactly
     * the out-of-stock set, so no combination returns something incoherent.
     */
    PageResponse<StockResponse> search(String search, boolean lowStockOnly,
                                       boolean outOfStockOnly, Pageable pageable);

    StockResponse get(Long productId);

    PageResponse<StockMovementResponse> movements(Long productId, Pageable pageable);

    /** Manual correction - stock take, damage, found stock. Gated by INVENTORY_ADJUST. */
    StockMovementResponse adjust(Long productId, StockAdjustmentRequest request);

    /**
     * The one sanctioned way for another module (Invoice) to move stock.
     * Locks the row, applies the signed change, writes the ledger entry, and
     * returns it - all inside the caller's transaction, so an invoice and
     * its stock movement commit or roll back together.
     */
    StockMovement applyMovement(Long productId, BigDecimal quantityChange, MovementType type,
                                String referenceType, Long referenceId, String notes);
}
