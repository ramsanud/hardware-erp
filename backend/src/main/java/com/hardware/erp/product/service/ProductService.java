package com.hardware.erp.product.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.product.dto.ProductDeletedResponse;
import com.hardware.erp.product.dto.ProductPriceHistoryResponse;
import com.hardware.erp.product.dto.ProductRequest;
import com.hardware.erp.product.dto.ProductResponse;
import com.hardware.erp.product.dto.ProductSummaryResponse;
import com.hardware.erp.product.entity.ProductStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse update(Long id, ProductRequest request);

    ProductResponse get(Long id);

    PageResponse<ProductSummaryResponse> search(String search, ProductStatus status,
                                                Long categoryId, Long brandId, Pageable pageable);

    void softDelete(Long id, Long actingUserId);

    /** CR-058: soft-deleted products for this tenant, newest deletion first. Invisible to every other query - see ProductRepository.findDeletedByTenantId. */
    List<ProductDeletedResponse> listDeleted();

    /** CR-058: undoes softDelete. 404 unless the row is this tenant's AND is genuinely deleted. */
    void restore(Long id);

    /** CR-053 backlog item 1. Most recent invoice lines for this product, newest first. */
    List<ProductPriceHistoryResponse> priceHistory(Long id);
}
