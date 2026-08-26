package com.hardware.erp.expense.service.impl;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.image.ImageValidation;
import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.expense.dto.BusinessExpenseRequest;
import com.hardware.erp.expense.dto.BusinessExpenseResponse;
import com.hardware.erp.expense.dto.ExpenseTotalResponse;
import com.hardware.erp.expense.entity.BusinessExpense;
import com.hardware.erp.expense.entity.ExpenseCategory;
import com.hardware.erp.expense.entity.ExpenseReceipt;
import com.hardware.erp.expense.entity.ExpenseStatus;
import com.hardware.erp.expense.mapper.ExpenseMapper;
import com.hardware.erp.expense.repository.BusinessExpenseRepository;
import com.hardware.erp.expense.repository.ExpenseCategoryRepository;
import com.hardware.erp.expense.repository.ExpenseReceiptRepository;
import com.hardware.erp.expense.service.BusinessExpenseService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BusinessExpenseServiceImpl implements BusinessExpenseService {

    private static final String MODULE = "EXPENSE";
    private static final String ENTITY = "BUSINESS_EXPENSE";

    private final BusinessExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseReceiptRepository receiptRepository;
    private final TenantRepository tenantRepository;
    private final ExpenseMapper mapper;
    private final ActivityLogService activityLog;

    @Override
    @Transactional
    public BusinessExpenseResponse create(BusinessExpenseRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        ExpenseCategory category = requireCategory(request.categoryId(), tenantId);

        BusinessExpense expense = BusinessExpense.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .expenseDate(request.expenseDate())
                .category(category)
                .amountPaise(request.amountPaise())
                .paymentMethod(request.paymentMethod())
                .notes(blankToNull(request.notes()))
                .status(ExpenseStatus.ACTIVE)
                .build();

        BusinessExpense saved = expenseRepository.save(expense);
        activityLog.created(MODULE, ENTITY, saved.getId(), category.getName(), snapshot(saved));
        return mapper.toResponse(saved, false);
    }

    @Override
    @Transactional
    public BusinessExpenseResponse update(Long id, BusinessExpenseRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        BusinessExpense expense = require(id, tenantId);
        Map<String, Object> before = snapshot(expense);

        ExpenseCategory category = requireCategory(request.categoryId(), tenantId);
        expense.setExpenseDate(request.expenseDate());
        expense.setCategory(category);
        expense.setAmountPaise(request.amountPaise());
        expense.setPaymentMethod(request.paymentMethod());
        expense.setNotes(blankToNull(request.notes()));

        BusinessExpense saved = expenseRepository.save(expense);
        activityLog.updated(MODULE, ENTITY, id, category.getName(), before, snapshot(saved));
        return mapper.toResponse(saved, receiptRepository.existsById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessExpenseResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        BusinessExpense expense = require(id, tenantId);
        return mapper.toResponse(expense, receiptRepository.existsById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BusinessExpenseResponse> search(String search, ExpenseStatus status, Long categoryId,
                                                          LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                expenseRepository.search(tenantId, blankToNull(search), status, categoryId, fromDate, toDate, pageable),
                expense -> mapper.toResponse(expense, receiptRepository.existsById(expense.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseTotalResponse total(LocalDate fromDate, LocalDate toDate) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        long paise = expenseRepository.totalAmountPaise(tenantId, ExpenseStatus.ACTIVE, fromDate, toDate);
        return new ExpenseTotalResponse(paise, IndianCurrencyFormat.rupees(paise));
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        BusinessExpense expense = require(id, tenantId);
        expense.setStatus(ExpenseStatus.CANCELLED);
        expenseRepository.save(expense);
        activityLog.deleted(MODULE, ENTITY, id, expense.getCategory().getName(), "Expense cancelled");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExpenseReceipt> getReceipt(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        require(id, tenantId);
        return receiptRepository.findById(id);
    }

    @Override
    @Transactional
    public void uploadReceipt(Long id, MultipartFile file) {
        ImageValidation.validate(file, ImageValidation.PHOTO_TYPES);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        require(id, tenantId);
        try {
            ExpenseReceipt receipt = receiptRepository.findById(id)
                    .orElse(ExpenseReceipt.builder().businessExpenseId(id).build());
            receipt.setContentType(file.getContentType());
            receipt.setFileSize((int) file.getSize());
            receipt.setImageData(file.getBytes());
            receipt.setUpdatedAt(LocalDateTime.now());
            receiptRepository.save(receipt);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded receipt image", e);
        }
    }

    @Override
    @Transactional
    public void removeReceipt(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        require(id, tenantId);
        receiptRepository.deleteById(id);
    }

    private BusinessExpense require(Long id, Long tenantId) {
        return expenseRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", id));
    }

    private ExpenseCategory requireCategory(Long categoryId, Long tenantId) {
        return categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense category", categoryId));
    }

    private Map<String, Object> snapshot(BusinessExpense expense) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("expenseDate", expense.getExpenseDate());
        values.put("categoryName", expense.getCategory().getName());
        values.put("amountPaise", expense.getAmountPaise());
        values.put("paymentMethod", expense.getPaymentMethod());
        values.put("status", expense.getStatus());
        return values;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
