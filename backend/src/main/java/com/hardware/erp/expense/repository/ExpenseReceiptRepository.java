package com.hardware.erp.expense.repository;

import com.hardware.erp.expense.entity.ExpenseReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseReceiptRepository extends JpaRepository<ExpenseReceipt, Long> {
}
