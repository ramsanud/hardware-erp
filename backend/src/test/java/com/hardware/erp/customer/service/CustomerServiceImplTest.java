package com.hardware.erp.customer.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.customer.dto.CustomerProductHistoryResponse;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.entity.CustomerStatus;
import com.hardware.erp.customer.mapper.CustomerMapper;
import com.hardware.erp.customer.repository.CustomerRepository;
import com.hardware.erp.customer.service.impl.CustomerServiceImpl;
import com.hardware.erp.invoice.mapper.InvoiceMapper;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.quotation.dto.QuotationSummaryResponse;
import com.hardware.erp.quotation.entity.Quotation;
import com.hardware.erp.quotation.entity.QuotationStatus;
import com.hardware.erp.quotation.mapper.QuotationMapper;
import com.hardware.erp.quotation.repository.QuotationRepository;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Customer 360 (CR-030) - covers only the two new methods this round added; the rest of CustomerServiceImpl has no prior unit test coverage to extend. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerServiceImplTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private QuotationRepository quotationRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private CustomerMapper customerMapper;
    @Mock private InvoiceMapper invoiceMapper;
    @Mock private QuotationMapper quotationMapper;
    @Mock private ActivityLogService activityLog;
    @Mock private com.hardware.erp.tenant.service.EntitlementService entitlementService;

    @InjectMocks private CustomerServiceImpl customerService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        Tenant tenant = Tenant.builder().id(1L).slug("default").name("Default").status(TenantStatus.ACTIVE).build();
        customer = Customer.builder().id(6L).customerCode("CUS-0006").customerName("Ram Sangar")
                .mobileNo("9999999999").status(CustomerStatus.ACTIVE).build();
        when(customerRepository.findByIdAndTenantId(6L, 1L)).thenReturn(Optional.of(customer));

        Role role = Role.builder().id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE).permissions(new LinkedHashSet<>()).build();
        User authUser = User.builder().id(1L).tenant(tenant).role(role)
                .fullName("Owner").mobileNo("9876543210").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(authUser), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("recentQuotations delegates to QuotationRepository.findByCustomer, tenant-scoped")
    void recentQuotationsDelegatesCorrectly() {
        Quotation quotation = Quotation.builder().id(1L).quotationNumber("QUO-0001")
                .customer(customer).status(QuotationStatus.SENT).build();
        when(quotationRepository.findByCustomer(1L, 6L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(quotation)));
        when(quotationMapper.toSummary(quotation)).thenReturn(
                new QuotationSummaryResponse(1L, "QUO-0001", "Ram Sangar", "9999999999",
                        LocalDate.now(), null, false, "1,000.00", QuotationStatus.SENT));

        PageResponse<QuotationSummaryResponse> result = customerService.recentQuotations(6L, PageRequest.of(0, 10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).quotationNumber()).isEqualTo("QUO-0001");
    }

    @Test
    @DisplayName("productHistory maps the native aggregate rows correctly, including the last-price display string")
    void productHistoryMapsRowsCorrectly() {
        Object[] row = {42L, "Anchor Switch", "PRD-000042", "PCS",
                BigDecimal.valueOf(15), 6500L, Date.valueOf(LocalDate.of(2026, 8, 1))};
        when(invoiceRepository.productPurchaseHistory(1L, 6L)).thenReturn(java.util.Collections.singletonList(row));

        List<CustomerProductHistoryResponse> result = customerService.productHistory(6L);

        assertThat(result).hasSize(1);
        CustomerProductHistoryResponse history = result.get(0);
        assertThat(history.productId()).isEqualTo(42L);
        assertThat(history.productName()).isEqualTo("Anchor Switch");
        assertThat(history.totalQuantityPurchased()).isEqualByComparingTo("15");
        assertThat(history.lastPriceDisplay()).isEqualTo("65.00");
        assertThat(history.lastPurchaseDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    @DisplayName("CR-031: create() checks the tenant's customer entitlement limit before anything is written")
    void createChecksEntitlement() {
        org.mockito.Mockito.doThrow(new com.hardware.erp.common.exception.BusinessException(
                        "Your Free plan allows up to 100 customers.",
                        org.springframework.http.HttpStatus.PAYMENT_REQUIRED, "ENTITLEMENT_LIMIT_REACHED"))
                .when(entitlementService).requireCanAddCustomer();

        var request = new com.hardware.erp.customer.dto.CustomerRequest(
                "New Customer", "9000000001", null, null, null, null, null, null, null, null,
                CustomerStatus.ACTIVE, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> customerService.create(request))
                .isInstanceOf(com.hardware.erp.common.exception.BusinessException.class)
                .extracting(ex -> ((com.hardware.erp.common.exception.BusinessException) ex).getCode())
                .isEqualTo("ENTITLEMENT_LIMIT_REACHED");
        org.mockito.Mockito.verify(customerRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(Customer.class));
    }

    @Test
    @DisplayName("update() can reactivate an INACTIVE customer back to ACTIVE (CR-030 §18-23)")
    void updateCanReactivateCustomer() {
        customer.setStatus(CustomerStatus.INACTIVE);
        when(customerRepository.save(org.mockito.ArgumentMatchers.any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new com.hardware.erp.customer.dto.CustomerRequest(
                "Ram Sangar", "9999999999", null, null, null, null, null, null, null, null,
                CustomerStatus.ACTIVE, null);
        customerService.update(6L, request);

        org.mockito.ArgumentCaptor<Customer> captor = org.mockito.ArgumentCaptor.forClass(Customer.class);
        org.mockito.Mockito.verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    @DisplayName("creditCheckByMobile returns the customer's credit limit and current outstanding balance when found")
    void creditCheckByMobileFindsExistingCustomer() {
        customer.setCreditLimitPaise(5_000_000L);
        when(customerRepository.findByTenantIdAndMobileNo(1L, "9999999999")).thenReturn(Optional.of(customer));
        when(invoiceRepository.customerFinancialSummary(1L, 6L))
                .thenReturn(java.util.Collections.singletonList(new Object[]{2L, 400000L, 150000L, 250000L}));

        Optional<com.hardware.erp.customer.dto.CustomerCreditCheckResponse> result =
                customerService.creditCheckByMobile("9999999999");

        assertThat(result).isPresent();
        assertThat(result.get().customerId()).isEqualTo(6L);
        assertThat(result.get().creditLimitPaise()).isEqualTo(5_000_000L);
        assertThat(result.get().outstandingBalancePaise()).isEqualTo(250000L);
    }

    @Test
    @DisplayName("creditCheckByMobile returns empty for a mobile number with no matching customer - a new walk-in, not an error")
    void creditCheckByMobileReturnsEmptyForUnknownMobile() {
        when(customerRepository.findByTenantIdAndMobileNo(1L, "9000000000")).thenReturn(Optional.empty());

        assertThat(customerService.creditCheckByMobile("9000000000")).isEmpty();
    }

    @Test
    @DisplayName("an unknown customer id is rejected, not silently ignored, on either new method")
    void unknownCustomerRejectedOnBothMethods() {
        when(customerRepository.findByIdAndTenantId(999L, 1L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> customerService.recentQuotations(999L, PageRequest.of(0, 10)))
                .isInstanceOf(com.hardware.erp.common.exception.ResourceNotFoundException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> customerService.productHistory(999L))
                .isInstanceOf(com.hardware.erp.common.exception.ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------
    // CR-058 - reactivation. Customer carries no deleted_at and no
    // @SQLRestriction, so an INACTIVE customer was never hidden: this is a
    // plain status change, not the Supplier/Product/User restore path.
    // ---------------------------------------------------------------

    @Test
    @DisplayName("CR-058: activate() puts an INACTIVE customer back to ACTIVE and logs a STATUS_CHANGE")
    void activateReactivatesInactiveCustomer() {
        Customer inactive = Customer.builder()
                .id(11L)
                .customerName("Meenakshi Traders")
                .mobileNo("9842000111")
                .status(CustomerStatus.INACTIVE)
                .build();
        when(customerRepository.findByIdAndTenantId(11L, 1L)).thenReturn(Optional.of(inactive));

        customerService.activate(11L);

        assertThat(inactive.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        // Reactivating is a business-record change, so it belongs in
        // activity_log - unlike User.restore, which re-enables a login and is
        // therefore a security event (CR-015, hard rule 8).
        org.mockito.Mockito.verify(activityLog).action(
                org.mockito.ArgumentMatchers.eq("CUSTOMER"),
                org.mockito.ArgumentMatchers.eq("CUSTOMER"),
                org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.eq("Meenakshi Traders"),
                org.mockito.ArgumentMatchers.eq(com.hardware.erp.common.activity.ActivityAction.STATUS_CHANGE),
                org.mockito.ArgumentMatchers.anyString());
        // The same row is updated in place - the customer keeps its id and
        // every invoice that references it.
        org.mockito.Mockito.verify(customerRepository).save(inactive);
    }

    @Test
    @DisplayName("CR-058: activate() on another tenant's customer is a 404, never a cross-tenant write")
    void cannotActivateAnotherTenantsCustomer() {
        // The signed-in principal is tenant 1; customer 11 belongs to tenant 2,
        // so the tenant-scoped lookup finds nothing.
        when(customerRepository.findByIdAndTenantId(11L, 1L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> customerService.activate(11L))
                .isInstanceOf(com.hardware.erp.common.exception.ResourceNotFoundException.class);

        org.mockito.Mockito.verify(customerRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(Customer.class));
        org.mockito.Mockito.verifyNoInteractions(activityLog);
    }

    @Test
    @DisplayName("CR-058: activate() on an unknown id is a 404")
    void activateUnknownIdGives404() {
        when(customerRepository.findByIdAndTenantId(999L, 1L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> customerService.activate(999L))
                .isInstanceOf(com.hardware.erp.common.exception.ResourceNotFoundException.class);
    }
}
