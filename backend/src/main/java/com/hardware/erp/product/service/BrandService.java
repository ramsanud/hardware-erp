package com.hardware.erp.product.service;

import com.hardware.erp.product.dto.BrandRequest;
import com.hardware.erp.product.dto.BrandResponse;

import java.util.List;

public interface BrandService {

    List<BrandResponse> findAll();

    BrandResponse get(Long id);

    BrandResponse create(BrandRequest request);

    BrandResponse update(Long id, BrandRequest request);

    void delete(Long id);
}
