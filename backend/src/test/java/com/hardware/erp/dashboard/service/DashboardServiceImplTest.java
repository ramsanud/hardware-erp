package com.hardware.erp.dashboard.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.dashboard.dto.SalesSummaryResponse;
import com.hardware.erp.dashboard.service.impl.DashboardServiceImpl;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Reproduces a real bug found by live-clicking the Dashboard with real
 * invoice data (CR-027 session): tenantSalesSummary() was declared to
 * return Object[] directly, but a JPQL multi-column aggregate query with no
 * GROUP BY is returned by Hibernate/Spring Data as a single-element
 * List&lt;Object[]&gt;, not a bare Object[] - assigning it straight to an
 * Object[] variable meant totals[0] was itself the real Object[] row, and
 * totals[1] didn't exist, so totals[0]/[1] were never real Long/Number
 * values. This had zero test coverage before now, which is exactly how it
 * shipped undetected.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceImplTest {

    @Mock private InvoiceRepository invoiceRepository;

    @InjectMocks private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        Tenant tenant = Tenant.builder().id(1L).slug("default").name("Default")
                .status(TenantStatus.ACTIVE).build();
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
    @DisplayName("real aggregate values come back as actual totals, not a nested array")
    void unwrapsTheSingleAggregateRow() {
        when(invoiceRepository.tenantSalesSummary(1L))
                .thenReturn(List.<Object[]>of(new Object[]{354000L, 177000L}));
        when(invoiceRepository.todaySales(anyLong(), any(LocalDate.class))).thenReturn(0L);

        SalesSummaryResponse summary = dashboardService.salesSummary();

        assertThat(summary.totalSalesDisplay()).isEqualTo("3,540.00");
        assertThat(summary.outstandingCustomerBalanceDisplay()).isEqualTo("1,770.00");
    }

    @Test
    @DisplayName("CR-033: today's and yesterday's sales are each the real figure for that specific date, not the same call reused")
    void reportsDistinctTodayAndYesterdayFigures() {
        when(invoiceRepository.tenantSalesSummary(1L)).thenReturn(List.<Object[]>of(new Object[]{0L, 0L}));
        when(invoiceRepository.todaySales(1L, LocalDate.now())).thenReturn(84250_00L);
        when(invoiceRepository.todaySales(1L, LocalDate.now().minusDays(1))).thenReturn(74700_00L);

        SalesSummaryResponse summary = dashboardService.salesSummary();

        assertThat(summary.todaySalesPaise()).isEqualTo(84250_00L);
        assertThat(summary.yesterdaySalesPaise()).isEqualTo(74700_00L);
    }

    @Test
    @DisplayName("a shop with zero invoices still gets a real (zero) summary, not an exception")
    void handlesNoInvoicesYet() {
        when(invoiceRepository.tenantSalesSummary(1L)).thenReturn(List.<Object[]>of(new Object[]{0L, 0L}));
        when(invoiceRepository.todaySales(anyLong(), any(LocalDate.class))).thenReturn(0L);

        SalesSummaryResponse summary = dashboardService.salesSummary();

        assertThat(summary.totalSalesDisplay()).isEqualTo("0.00");
    }
}
