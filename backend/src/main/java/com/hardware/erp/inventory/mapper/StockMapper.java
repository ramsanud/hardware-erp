package com.hardware.erp.inventory.mapper;

import com.hardware.erp.inventory.dto.StockMovementResponse;
import com.hardware.erp.inventory.dto.StockResponse;
import com.hardware.erp.inventory.entity.Stock;
import com.hardware.erp.inventory.entity.StockMovement;
import org.springframework.stereotype.Component;

@Component
public class StockMapper {

    public StockResponse toResponse(Stock stock) {
        boolean lowStock = stock.getQuantityOnHand()
                .compareTo(stock.getProduct().getReorderLevel()) <= 0;
        return new StockResponse(
                stock.getProduct().getId(),
                stock.getProduct().getProductCode(),
                stock.getProduct().getProductName(),
                stock.getProduct().getUnit(),
                stock.getQuantityOnHand(),
                stock.getProduct().getReorderLevel(),
                lowStock);
    }

    public StockMovementResponse toResponse(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getMovementType(),
                movement.getQuantityChange(),
                movement.getBalanceAfter(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getNotes(),
                movement.getCreatedAt());
    }
}
