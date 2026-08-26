package com.hardware.erp.invoice.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import com.hardware.erp.invoice.entity.Payment;
import com.hardware.erp.invoice.entity.PaymentMethod;
import com.hardware.erp.invoice.dto.PaymentSummaryResponse;
import com.hardware.erp.invoice.mapper.InvoiceMapper;
import com.hardware.erp.invoice.repository.PaymentRepository;
import com.hardware.erp.invoice.service.impl.PaymentServiceImpl;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;

    @Spy private InvoiceMapper invoiceMapper = new InvoiceMapper();

    @InjectMocks private PaymentServiceImpl paymentService;

    private Tenant tenant;
    private Payment payment;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).slug("default").name("Default")
                .status(TenantStatus.ACTIVE).build();

        Customer customer = Customer.builder().id(3L).tenant(tenant).customerCode("CUS-0001")
                .customerName("Ramesh Traders").mobileNo("9876500001").build();

        Invoice invoice = Invoice.builder().id(1L).tenant(tenant).invoiceNumber("INV-000001")
                .customer(customer).invoiceDate(LocalDate.now())
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .paidPaise(35400L).balancePaise(0L).status(InvoiceStatus.PAID)
                .build();

        payment = Payment.builder().id(11L).tenant(tenant).invoice(invoice)
                .amountPaise(35400L).paymentMethod(PaymentMethod.UPI)
                .paymentDate(LocalDateTime.now()).notes("Full settlement").build();

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
    @DisplayName("search maps repository results into the summary DTO, page metadata included")
    void searchDelegatesAndMaps() {
        Pageable pageable = PageRequest.of(0, 20);
        when(paymentRepository.search(eq(1L), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(payment), pageable, 1));

        PageResponse<PaymentSummaryResponse> response =
                paymentService.search(null, null, null, null, pageable);

        assertThat(response.totalElements()).isEqualTo(1);
        PaymentSummaryResponse summary = response.content().get(0);
        assertThat(summary.id()).isEqualTo(11L);
        assertThat(summary.invoiceId()).isEqualTo(1L);
        assertThat(summary.invoiceNumber()).isEqualTo("INV-000001");
        assertThat(summary.customerName()).isEqualTo("Ramesh Traders");
        assertThat(summary.customerMobile()).isEqualTo("9876500001");
        assertThat(summary.amountDisplay()).isEqualTo("354.00");
        assertThat(summary.paymentMethod()).isEqualTo(PaymentMethod.UPI);
        assertThat(summary.notes()).isEqualTo("Full settlement");
    }

    @Test
    @DisplayName("a fromDate/toDate pair widens to the full calendar day before hitting the repository")
    void dateRangeWidensToFullDay() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 22);
        when(paymentRepository.search(anyLong(), any(), any(), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        paymentService.search(null, null, from, to, pageable);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(paymentRepository).search(eq(1L), isNull(), isNull(),
                fromCaptor.capture(), toCaptor.capture(), eq(pageable));

        assertThat(fromCaptor.getValue()).isEqualTo(from.atStartOfDay());
        assertThat(toCaptor.getValue()).isEqualTo(LocalDateTime.of(to, LocalTime.MAX));
    }

    @Test
    @DisplayName("the search is always scoped to the caller's own tenant, taken from the security context")
    void tenantScopingComesFromSecurityContext() {
        Pageable pageable = PageRequest.of(0, 20);
        when(paymentRepository.search(anyLong(), any(), any(), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(payment), pageable, 1));

        paymentService.search("ramesh", PaymentMethod.UPI, null, null, pageable);

        ArgumentCaptor<Long> tenantCaptor = ArgumentCaptor.forClass(Long.class);
        // The authenticated user in setUp() belongs to tenant 1 - the search
        // must never be scoped to any tenant id supplied by a caller, because
        // there is no such parameter: it can only come from SecurityUtils.
        verify(paymentRepository).search(tenantCaptor.capture(), eq("ramesh"),
                eq(PaymentMethod.UPI), isNull(), isNull(), eq(pageable));
        assertThat(tenantCaptor.getValue()).isEqualTo(1L);
    }
}
