package com.hardware.erp.expense.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.expense.dto.ExpenseCategoryRequest;
import com.hardware.erp.expense.dto.ExpenseCategoryResponse;
import com.hardware.erp.expense.service.ExpenseCategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** User-extensible categories for the expense ledger (CR-036 phase 3) - not a fixed list. */
@RestController
@RequestMapping("/v1/expense-categories")
@RequiredArgsConstructor
@Tag(name = "Expenses")
public class ExpenseCategoryController {

    private final ExpenseCategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).EXPENSE_VIEW)")
    public ApiResponse<List<ExpenseCategoryResponse>> list() {
        return ApiResponse.ok(categoryService.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).EXPENSE_MANAGE)")
    public ApiResponse<ExpenseCategoryResponse> create(@Valid @RequestBody ExpenseCategoryRequest request) {
        return ApiResponse.ok(categoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).EXPENSE_MANAGE)")
    public ApiResponse<ExpenseCategoryResponse> update(
            @PathVariable Long id, @Valid @RequestBody ExpenseCategoryRequest request) {
        return ApiResponse.ok(categoryService.update(id, request));
    }
}
