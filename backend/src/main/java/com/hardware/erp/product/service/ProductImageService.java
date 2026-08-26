package com.hardware.erp.product.service;

import com.hardware.erp.product.entity.ProductImage;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/** Backs the Product form's photo upload (CR-036) - one image per product, tenant-scoped since the image endpoint takes a product id directly. */
public interface ProductImageService {

    Optional<ProductImage> get(Long productId);

    void upload(Long productId, MultipartFile file);

    void remove(Long productId);
}
