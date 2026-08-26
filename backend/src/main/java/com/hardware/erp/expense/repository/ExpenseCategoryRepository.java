package com.hardware.erp.expense.repository;

import com.hardware.erp.expense.entity.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    List<ExpenseCategory> findByTenantIdOrderByNameAsc(Long tenantId);

    Optional<ExpenseCategory> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByNameIgnoreCaseAndTenantId(String name, Long tenantId);

    boolean existsByNameIgnoreCaseAndTenantIdAndIdNot(String name, Long tenantId, Long id);
}
