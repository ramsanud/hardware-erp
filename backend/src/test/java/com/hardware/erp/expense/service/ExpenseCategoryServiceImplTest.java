package com.hardware.erp.expense.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.expense.dto.ExpenseCategoryRequest;
import com.hardware.erp.expense.dto.ExpenseCategoryResponse;
import com.hardware.erp.expense.entity.ExpenseCategory;
import com.hardware.erp.expense.mapper.ExpenseMapper;
import com.hardware.erp.expense.repository.ExpenseCategoryRepository;
import com.hardware.erp.expense.service.impl.ExpenseCategoryServiceImpl;
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

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpenseCategoryServiceImplTest {

    @Mock private ExpenseCategoryRepository categoryRepository;
    @Mock private TenantRepository tenantRepository;

    private ExpenseCategoryServiceImpl service;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        service = new ExpenseCategoryServiceImpl(categoryRepository, tenantRepository, new ExpenseMapper());

        tenant = Tenant.builder().id(1L).slug("default").name("Default Shop").status(TenantStatus.ACTIVE).build();
        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(categoryRepository.save(any(ExpenseCategory.class))).thenAnswer(inv -> {
            ExpenseCategory c = inv.getArgument(0);
            if (c.getId() == null) c.setId(10L);
            return c;
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

    @Test
    void createsANewCategory() {
        ExpenseCategoryResponse response = service.create(new ExpenseCategoryRequest("Rent", null));

        assertThat(response.name()).isEqualTo("Rent");
    }

    @Test
    void rejectsADuplicateCategoryNameCaseInsensitively() {
        when(categoryRepository.existsByNameIgnoreCaseAndTenantId("rent", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new ExpenseCategoryRequest("rent", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }
}
