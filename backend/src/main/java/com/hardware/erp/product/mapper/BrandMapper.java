package com.hardware.erp.product.mapper;

import com.hardware.erp.product.dto.BrandResponse;
import com.hardware.erp.product.entity.Brand;
import org.springframework.stereotype.Component;

@Component
public class BrandMapper {

    public BrandResponse toResponse(Brand brand, long productCount) {
        return new BrandResponse(
                brand.getId(),
                brand.getBrandCode(),
                brand.getBrandName(),
                brand.getDescription(),
                brand.getStatus(),
                productCount);
    }
}
