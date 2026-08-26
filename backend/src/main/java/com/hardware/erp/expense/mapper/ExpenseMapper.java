package com.hardware.erp.expense.mapper;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.expense.dto.BusinessExpenseResponse;
import com.hardware.erp.expense.dto.ExpenseCategoryResponse;
import com.hardware.erp.expense.entity.BusinessExpense;
import com.hardware.erp.expense.entity.ExpenseCategory;
import org.springframework.stereotype.Component;

@Component
public class ExpenseMapper {

    public ExpenseCategoryResponse toResponse(ExpenseCategory category) {
        return new ExpenseCategoryResponse(category.getId(), category.getName(), category.getDescription());
    }

    public BusinessExpenseResponse toResponse(BusinessExpense expense, boolean hasReceipt) {
        return new BusinessExpenseResponse(
                expense.getId(),
                expense.getExpenseDate(),
                expense.getCategory().getId(),
                expense.getCategory().getName(),
                expense.getAmountPaise(),
                IndianCurrencyFormat.rupees(expense.getAmountPaise()),
                expense.getPaymentMethod(),
                expense.getNotes(),
                expense.getStatus(),
                hasReceipt,
                expense.getCreatedAt());
    }
}
