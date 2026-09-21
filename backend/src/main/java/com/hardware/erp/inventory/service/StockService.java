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

    /**
     * CR-091 Phase 6. A purchase receipt, PLUS the weighted-average cost
     * update that ordinary applyMovement() never does (every other
     * MovementType leaves cost alone - a sale, adjustment or return does not
     * change what the shelf's average unit cost is).
     *
     * A default method rather than a new parameter on applyMovement() -
     * CLAUDE.md's own rule for growing a provider interface - so every
     * existing SALE/ADJUSTMENT/RETURN caller is untouched. Not truly
     * "default" (it needs the repositories), so it is declared abstract
     * here and implemented once in StockServiceImpl, the only implementer.
     *
     * newAverage = (oldAverage * oldQty + unitCostPaise * receivedQty) / (oldQty + receivedQty),
     * rounded to the nearest paisa. A shop that has never recorded a cost
     * (average 0, e.g. straight off V62's product-price backfill) simply
     * adopts this receipt's cost outright.
     */
    StockMovement applyPurchaseReceipt(Long productId, BigDecimal quantityChange, Long unitCostPaise,
                                       String referenceType, Long referenceId, String notes);

    /**
     * CR-092. Moves {@code quantity} of a product from one branch to another:
     * one STOCK_TRANSFER_OUT movement at the source and one STOCK_TRANSFER_IN
     * at the destination, the branch breakdown adjusted for both, and the
     * tenant-level stock row untouched - a transfer changes where goods are,
     * not how many the shop has. Refused when the source branch does not
     * hold enough (the one place branch_stock IS enforced, because a
     * transfer is exactly a claim about a branch's own holding).
     */
    void applyBranchTransfer(Long productId, BigDecimal quantity, Long fromBranchId, Long toBranchId,
                             Long transferId, String transferNumber);
}
