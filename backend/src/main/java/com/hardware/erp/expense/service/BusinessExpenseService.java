package com.hardware.erp.expense.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.expense.dto.BusinessExpenseRequest;
import com.hardware.erp.expense.dto.BusinessExpenseResponse;
import com.hardware.erp.expense.dto.ExpenseTotalResponse;
import com.hardware.erp.expense.entity.ExpenseReceipt;
import com.hardware.erp.expense.entity.ExpenseStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Optional;

public interface BusinessExpenseService {

    BusinessExpenseResponse create(BusinessExpenseRequest request);

    BusinessExpenseResponse update(Long id, BusinessExpenseRequest request);

    BusinessExpenseResponse get(Long id);

    PageResponse<BusinessExpenseResponse> search(String search, ExpenseStatus status, Long categoryId,
                                                  LocalDate fromDate, LocalDate toDate, Pageable pageable);

    /** Running total for the same filters search() would use, over ACTIVE expenses only. */
    ExpenseTotalResponse total(LocalDate fromDate, LocalDate toDate);

    /** Soft-cancel - a recorded expense is a financial record, never hard-deleted. */
    void cancel(Long id);

    Optional<ExpenseReceipt> getReceipt(Long id);

    void uploadReceipt(Long id, MultipartFile file);

    void removeReceipt(Long id);
}
