package com.hardware.erp.product.mapper;

import com.hardware.erp.product.dto.CategoryResponse;
import com.hardware.erp.product.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category, long productCount) {
        return new CategoryResponse(
                category.getId(),
                category.getCategoryCode(),
                category.getCategoryName(),
                category.getParent() == null ? null : category.getParent().getId(),
                category.getParent() == null ? null : category.getParent().getCategoryName(),
                category.getDescription(),
                category.getStatus(),
                productCount);
    }
}
