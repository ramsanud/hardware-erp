package com.hardware.erp.expense.repository;

import com.hardware.erp.expense.entity.BusinessExpense;
import com.hardware.erp.expense.entity.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface BusinessExpenseRepository extends JpaRepository<BusinessExpense, Long> {

    Optional<BusinessExpense> findByIdAndTenantId(Long id, Long tenantId);

    /** Platform Admin tenant usage summary. */
    long countByTenantId(Long tenantId);

    // (:fromDate is null or ...) with a bare, uncast parameter fails at the
    // database with "could not determine data type of parameter" whenever
    // fromDate/toDate is actually null (the "is null" side alone gives
    // PostgreSQL's prepared-statement type inference nothing to go on) -
    // the exact BUG-SUP-004/BUG-PAY-001/BUG-SEC-002 class of defect (see
    // BUG_REGISTRY.md), found live testing this query's simpler
    // totalAmountPaise() sibling below with no date range given (the
    // common case - most owners load the ledger with no filter first).
    // Fix: cast(:param as date) on the "is null" occurrence ONLY, exactly
    // matching SecurityAuditLogRepository's own working fix for the same
    // problem - casting the *comparison* occurrence too (e.g.
    // "e.expenseDate >= cast(:fromDate as date)") throws a completely
    // different error ("cannot cast type bytea to date"), a real trap:
    // the fix looks more "consistent" that way but actually breaks it.
    @Query("""
           select e from BusinessExpense e
           where e.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(e.notes) like lower(concat('%', cast(:search as string), '%'))
                  or lower(e.category.name) like lower(concat('%', cast(:search as string), '%')))
             and (:status is null or e.status = :status)
             and (:categoryId is null or e.category.id = :categoryId)
             and (cast(:fromDate as date) is null or e.expenseDate >= :fromDate)
             and (cast(:toDate as date) is null or e.expenseDate <= :toDate)
           order by e.expenseDate desc, e.id desc
           """)
    Page<BusinessExpense> search(@Param("tenantId") Long tenantId,
                                  @Param("search") String search,
                                  @Param("status") ExpenseStatus status,
                                  @Param("categoryId") Long categoryId,
                                  @Param("fromDate") LocalDate fromDate,
                                  @Param("toDate") LocalDate toDate,
                                  Pageable pageable);

    @Query("""
           select coalesce(sum(e.amountPaise), 0) from BusinessExpense e
           where e.tenant.id = :tenantId and e.status = :status
             and (cast(:fromDate as date) is null or e.expenseDate >= :fromDate)
             and (cast(:toDate as date) is null or e.expenseDate <= :toDate)
           """)
    long totalAmountPaise(@Param("tenantId") Long tenantId,
                           @Param("status") ExpenseStatus status,
                           @Param("fromDate") LocalDate fromDate,
                           @Param("toDate") LocalDate toDate);
}
