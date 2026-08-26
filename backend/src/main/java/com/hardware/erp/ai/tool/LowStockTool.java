package com.hardware.erp.ai.tool;

import com.hardware.erp.auth.entity.PermissionCode;
import com.hardware.erp.inventory.dto.StockResponse;
import com.hardware.erp.inventory.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LowStockTool implements AiTool {

    private final StockService stockService;

    @Override
    public String name() {
        return "get_low_stock_products";
    }

    @Override
    public String description() {
        return "Lists products whose stock on hand is at or below their reorder level right now.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of();
    }

    @Override
    public String requiredPermission() {
        return PermissionCode.INVENTORY_VIEW;
    }

    @Override
    public String execute(Map<String, String> args) {
        List<StockResponse> lowStock = stockService.search(null, true, PageRequest.of(0, 20)).content();
        if (lowStock.isEmpty()) {
            return "No products are currently at or below their reorder level.";
        }
        StringBuilder sb = new StringBuilder(lowStock.size() + " product(s) at or below reorder level:\n");
        for (StockResponse row : lowStock) {
            sb.append("- ").append(row.productName()).append(" (").append(row.productCode()).append("): ")
                    .append(row.quantityOnHand().stripTrailingZeros().toPlainString()).append(' ').append(row.unit())
                    .append(" on hand, reorder level ")
                    .append(row.reorderLevel().stripTrailingZeros().toPlainString()).append('\n');
        }
        return sb.toString();
    }
}
