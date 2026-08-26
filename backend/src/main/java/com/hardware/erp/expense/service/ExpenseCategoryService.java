package com.hardware.erp.expense.service;

import com.hardware.erp.expense.dto.ExpenseCategoryRequest;
import com.hardware.erp.expense.dto.ExpenseCategoryResponse;

import java.util.List;

public interface ExpenseCategoryService {

    List<ExpenseCategoryResponse> list();

    ExpenseCategoryResponse create(ExpenseCategoryRequest request);

    ExpenseCategoryResponse update(Long id, ExpenseCategoryRequest request);
}
