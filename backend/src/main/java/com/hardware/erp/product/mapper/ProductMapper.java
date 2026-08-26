package com.hardware.erp.product.mapper;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.product.dto.ProductResponse;
import com.hardware.erp.product.dto.ProductSummaryResponse;
import com.hardware.erp.product.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    /**
     * includeCost gates purchasePricePaise/purchasePriceDisplay. Counter
     * staff (no PRODUCT_VIEW_COST) must not see purchase price or margin -
     * the same rule Module 1's STAFF role already enforces.
     */
    public ProductResponse toResponse(Product product, boolean includeCost, boolean hasImage) {
        return new ProductResponse(
                product.getId(),
                product.getProductCode(),
                product.getProductName(),
                product.getCategory() == null ? null : product.getCategory().getId(),
                product.getCategory() == null ? null : product.getCategory().getCategoryName(),
                product.getBrand() == null ? null : product.getBrand().getId(),
                product.getBrand() == null ? null : product.getBrand().getBrandName(),
                product.getModelNo(),
                product.getManufacturerCode(),
                product.getBarcode(),
                product.getUnit(),
                product.getDescription(),
                product.getHsnCode(),
                product.getGstRatePercent(),
                includeCost ? product.getPurchasePricePaise() : null,
                includeCost ? rupees(product.getPurchasePricePaise()) : null,
                product.getSellingPricePaise(),
                rupees(product.getSellingPricePaise()),
                product.getMrpPaise(),
                rupees(product.getMrpPaise()),
                product.getMinimumStock(),
                product.getReorderLevel(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                hasImage);
    }

    public ProductSummaryResponse toSummary(Product product, boolean hasImage) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getProductCode(),
                product.getProductName(),
                product.getCategory() == null ? null : product.getCategory().getCategoryName(),
                product.getBrand() == null ? null : product.getBrand().getBrandName(),
                product.getUnit(),
                rupees(product.getSellingPricePaise()),
                product.getGstRatePercent() == null ? "0.00" : product.getGstRatePercent().toPlainString(),
                product.getStatus(),
                hasImage);
    }

    /** Paise to a displayable rupee string, Indian grouping. */
    public String rupees(Long paise) {
        return IndianCurrencyFormat.rupees(paise);
    }
}
