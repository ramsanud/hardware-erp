package com.hardware.erp.product.repository;

import com.hardware.erp.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /** Backs the product list page's thumbnail-or-fallback rendering - just which ids have an image, never the bytes themselves. */
    @Query("select p.productId from ProductImage p where p.productId in :productIds")
    List<Long> findProductIdsWithImage(@Param("productIds") Set<Long> productIds);
}
