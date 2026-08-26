package com.hardware.erp.product.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.product.dto.ProductRequest;
import com.hardware.erp.product.dto.ProductResponse;
import com.hardware.erp.product.dto.ProductSummaryResponse;
import com.hardware.erp.product.entity.ProductStatus;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse update(Long id, ProductRequest request);

    ProductResponse get(Long id);

    PageResponse<ProductSummaryResponse> search(String search, ProductStatus status,
                                                Long categoryId, Long brandId, Pageable pageable);

    void softDelete(Long id, Long actingUserId);
}
