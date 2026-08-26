package com.hardware.erp.expense.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.expense.dto.BusinessExpenseRequest;
import com.hardware.erp.expense.dto.BusinessExpenseResponse;
import com.hardware.erp.expense.dto.ExpenseTotalResponse;
import com.hardware.erp.expense.entity.ExpenseStatus;
import com.hardware.erp.expense.service.BusinessExpenseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/** A standalone shop-wide expense ledger (CR-036 phase 3) - deliberately separate from project.controller's own project-expense endpoints, which stay scoped to individual projects. */
@RestController
@RequestMapping("/v1/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses")
public class BusinessExpenseController {

    private final BusinessExpenseService expenseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).EXPENSE_MANAGE)")
    public ApiResponse<BusinessExpenseResponse> create(@Valid @RequestBody BusinessExpenseRequest request) {
        return ApiResponse.ok(expenseService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).EXPENSE_VIEW)")
    public ApiResponse<PageResponse<BusinessExpenseResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(expenseService.search(search, status, categoryId, fromDate, toDate, pageable));
    }

    @GetMapping("/total")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).EXPENSE_VIEW)")
    public ApiResponse<ExpenseTotalResponse> total(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.ok(expenseService.total(fromDate, toDate));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).EXPENSE_VIEW)")
    public ApiResponse<BusinessExpenseResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(expenseService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).EXPENSE_MANAGE)")
    public ApiResponse<BusinessExpenseResponse> update(
            @PathVariable Long id, @Valid @RequestBody BusinessExpenseRequest request) {
        return ApiResponse.ok(expenseService.update(id, request));
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).EXPENSE_MANAGE)")
    public void cancel(@PathVariable Long id) {
        expenseService.cancel(id);
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> getReceipt(@PathVariable Long id) {
        return expenseService.getReceipt(id)
                .map(receipt -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(receipt.getContentType()))
                        .body(receipt.getImageData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/{id}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).EXPENSE_MANAGE)")
    public void uploadReceipt(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        expenseService.uploadReceipt(id, file);
    }

    @DeleteMapping("/{id}/receipt")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).EXPENSE_MANAGE)")
    public void removeReceipt(@PathVariable Long id) {
        expenseService.removeReceipt(id);
    }
}
