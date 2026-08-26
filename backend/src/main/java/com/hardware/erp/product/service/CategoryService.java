package com.hardware.erp.product.service;

import com.hardware.erp.product.dto.CategoryRequest;
import com.hardware.erp.product.dto.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> findAll();

    CategoryResponse get(Long id);

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Long id, CategoryRequest request);

    void delete(Long id);
}
