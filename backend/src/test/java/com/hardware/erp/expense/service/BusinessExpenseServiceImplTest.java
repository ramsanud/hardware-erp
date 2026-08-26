package com.hardware.erp.expense.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.expense.dto.BusinessExpenseRequest;
import com.hardware.erp.expense.dto.BusinessExpenseResponse;
import com.hardware.erp.expense.entity.BusinessExpense;
import com.hardware.erp.expense.entity.ExpenseCategory;
import com.hardware.erp.expense.entity.ExpenseStatus;
import com.hardware.erp.expense.mapper.ExpenseMapper;
import com.hardware.erp.expense.repository.BusinessExpenseRepository;
import com.hardware.erp.expense.repository.ExpenseCategoryRepository;
import com.hardware.erp.expense.repository.ExpenseReceiptRepository;
import com.hardware.erp.expense.service.impl.BusinessExpenseServiceImpl;
import com.hardware.erp.invoice.entity.PaymentMethod;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BusinessExpenseServiceImplTest {

    @Mock private BusinessExpenseRepository expenseRepository;
    @Mock private ExpenseCategoryRepository categoryRepository;
    @Mock private ExpenseReceiptRepository receiptRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;

    private BusinessExpenseServiceImpl service;
    private Tenant tenant;
    private ExpenseCategory category;

    @BeforeEach
    void setUp() {
        service = new BusinessExpenseServiceImpl(expenseRepository, categoryRepository, receiptRepository,
                tenantRepository, new ExpenseMapper(), activityLog);

        tenant = Tenant.builder().id(1L).slug("default").name("Default Shop").status(TenantStatus.ACTIVE).build();
        category = ExpenseCategory.builder().id(5L).tenant(tenant).name("Rent").build();
        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(categoryRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(category));
        when(expenseRepository.save(any(BusinessExpense.class))).thenAnswer(inv -> {
            BusinessExpense e = inv.getArgument(0);
            if (e.getId() == null) e.setId(100L);
            return e;
        });

        Role role = Role.builder().id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE).permissions(new LinkedHashSet<>()).build();
        User authUser = User.builder().id(1L).tenant(tenant).role(role)
                .fullName("Owner").mobileNo("9999999999").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(authUser), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private BusinessExpenseRequest request() {
        return new BusinessExpenseRequest(LocalDate.of(2026, 8, 1), 5L, 500000L, PaymentMethod.BANK_TRANSFER, "August rent");
    }

    @Test
    void createsAnExpenseAgainstAnExistingCategory() {
        BusinessExpenseResponse response = service.create(request());

        assertThat(response.categoryName()).isEqualTo("Rent");
        assertThat(response.amountPaise()).isEqualTo(500000L);
        assertThat(response.status()).isEqualTo(ExpenseStatus.ACTIVE);
        verify(activityLog).created(eq("EXPENSE"), eq("BUSINESS_EXPENSE"), any(), eq("Rent"), any());
    }

    @Test
    void rejectsAnExpenseAgainstAnotherTenantsCategory() {
        when(categoryRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());
        BusinessExpenseRequest badRequest = new BusinessExpenseRequest(
                LocalDate.of(2026, 8, 1), 99L, 500000L, PaymentMethod.CASH, null);

        assertThatThrownBy(() -> service.create(badRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelSoftDeletesRatherThanRemovingTheRow() {
        BusinessExpense existing = BusinessExpense.builder().id(7L).tenant(tenant).category(category)
                .expenseDate(LocalDate.now()).amountPaise(1000L).paymentMethod(PaymentMethod.CASH)
                .status(ExpenseStatus.ACTIVE).build();
        when(expenseRepository.findByIdAndTenantId(7L, 1L)).thenReturn(Optional.of(existing));

        service.cancel(7L);

        assertThat(existing.getStatus()).isEqualTo(ExpenseStatus.CANCELLED);
        verify(expenseRepository).save(existing);
        verify(expenseRepository, never()).delete(any());
        verify(expenseRepository, never()).deleteById(any());
    }

    @Test
    void getForAnotherTenantsExpenseThrowsNotFound() {
        when(expenseRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(42L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
