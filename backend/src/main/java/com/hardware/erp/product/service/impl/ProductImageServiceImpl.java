package com.hardware.erp.product.service.impl;

import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.image.ImageValidation;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductImage;
import com.hardware.erp.product.repository.ProductImageRepository;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.product.service.ProductImageService;
import com.hardware.erp.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductImage> get(Long productId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        require(productId, tenantId);
        return productImageRepository.findById(productId);
    }

    @Override
    @Transactional
    public void upload(Long productId, MultipartFile file) {
        ImageValidation.validate(file, ImageValidation.PHOTO_TYPES);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        require(productId, tenantId);
        try {
            ProductImage image = productImageRepository.findById(productId)
                    .orElse(ProductImage.builder().productId(productId).build());
            image.setContentType(file.getContentType());
            image.setFileSize((int) file.getSize());
            image.setImageData(file.getBytes());
            image.setUpdatedAt(LocalDateTime.now());
            productImageRepository.save(image);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded image", e);
        }
    }

    @Override
    @Transactional
    public void remove(Long productId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        require(productId, tenantId);
        productImageRepository.deleteById(productId);
    }

    private Product require(Long productId, Long tenantId) {
        return productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }
}
