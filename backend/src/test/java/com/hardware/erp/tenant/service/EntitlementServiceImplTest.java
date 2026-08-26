package com.hardware.erp.tenant.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.customer.entity.CustomerStatus;
import com.hardware.erp.customer.repository.CustomerRepository;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.supplier.entity.SupplierStatus;
import com.hardware.erp.supplier.repository.SupplierRepository;
import com.hardware.erp.tenant.dto.UsageSummaryResponse;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.service.impl.EntitlementServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * CR-031 (Customer 360 §27-40) - subscription-tier entitlement limits.
 * FREE = 1 owner/100 customers/100 suppliers/1000 products, PRO =
 * 2/1000/1000/10000, MAX = unlimited (SubscriptionTier.UNLIMITED, -1).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EntitlementServiceImplTest {

    @Mock private SubscriptionService subscriptionService;
    @Mock private UserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private ProductRepository productRepository;

    private EntitlementServiceImpl entitlementService;

    @BeforeEach
    void setUp() {
        entitlementService = new EntitlementServiceImpl(
                subscriptionService, userRepository, customerRepository, supplierRepository, productRepository);

        Tenant tenant = Tenant.builder().id(1L).slug("default").name("Default").status(TenantStatus.ACTIVE).build();
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
    @DisplayName("requireCanAddCustomer rejects once the FREE tier's 100-customer limit is reached")
    void rejectsAtFreeCustomerLimit() {
        when(subscriptionService.currentTier()).thenReturn(SubscriptionTier.FREE);
        when(customerRepository.countByStatusAndTenantId(CustomerStatus.ACTIVE, 1L)).thenReturn(100L);

        assertThatThrownBy(() -> entitlementService.requireCanAddCustomer())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("ENTITLEMENT_LIMIT_REACHED");
    }

    @Test
    @DisplayName("requireCanAddCustomer allows the 99th customer, one under the FREE limit")
    void allowsOneUnderFreeCustomerLimit() {
        when(subscriptionService.currentTier()).thenReturn(SubscriptionTier.FREE);
        when(customerRepository.countByStatusAndTenantId(CustomerStatus.ACTIVE, 1L)).thenReturn(99L);

        assertThatCode(() -> entitlementService.requireCanAddCustomer()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireCanAddOwner rejects the second owner on FREE (limit is 1)")
    void rejectsSecondOwnerOnFree() {
        when(subscriptionService.currentTier()).thenReturn(SubscriptionTier.FREE);
        when(userRepository.countActiveOwners(1L)).thenReturn(1L);

        assertThatThrownBy(() -> entitlementService.requireCanAddOwner())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Free");
    }

    @Test
    @DisplayName("requireCanAddOwner allows a second owner on PRO (limit is 2)")
    void allowsSecondOwnerOnPro() {
        when(subscriptionService.currentTier()).thenReturn(SubscriptionTier.PRO);
        when(userRepository.countActiveOwners(1L)).thenReturn(1L);

        assertThatCode(() -> entitlementService.requireCanAddOwner()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireCanAddSupplier rejects at the PRO tier's 1000-supplier limit")
    void rejectsAtProSupplierLimit() {
        when(subscriptionService.currentTier()).thenReturn(SubscriptionTier.PRO);
        when(supplierRepository.countByStatusAndTenantId(SupplierStatus.ACTIVE, 1L)).thenReturn(1000L);

        assertThatThrownBy(() -> entitlementService.requireCanAddSupplier())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("MAX tier never rejects, no matter how high the count is")
    void maxTierIsUnlimited() {
        when(subscriptionService.currentTier()).thenReturn(SubscriptionTier.MAX);
        when(customerRepository.countByStatusAndTenantId(CustomerStatus.ACTIVE, 1L)).thenReturn(1_000_000L);
        when(supplierRepository.countByStatusAndTenantId(SupplierStatus.ACTIVE, 1L)).thenReturn(1_000_000L);
        when(productRepository.countByStatusAndTenantId(ProductStatus.ACTIVE, 1L)).thenReturn(1_000_000L);
        when(userRepository.countActiveOwners(1L)).thenReturn(1_000_000L);

        assertThatCode(() -> {
            entitlementService.requireCanAddCustomer();
            entitlementService.requireCanAddSupplier();
            entitlementService.requireCanAddProduct();
            entitlementService.requireCanAddOwner();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("usageSummary reports the tier's limits alongside the tenant's real active counts")
    void usageSummaryReportsRealCounts() {
        when(subscriptionService.currentTier()).thenReturn(SubscriptionTier.FREE);
        when(userRepository.countActiveOwners(1L)).thenReturn(1L);
        when(customerRepository.countByStatusAndTenantId(CustomerStatus.ACTIVE, 1L)).thenReturn(42L);
        when(supplierRepository.countByStatusAndTenantId(SupplierStatus.ACTIVE, 1L)).thenReturn(7L);
        when(productRepository.countByStatusAndTenantId(ProductStatus.ACTIVE, 1L)).thenReturn(300L);

        UsageSummaryResponse usage = entitlementService.usageSummary();

        assertThat(usage.tier()).isEqualTo(SubscriptionTier.FREE);
        assertThat(usage.ownerCount()).isEqualTo(1L);
        assertThat(usage.maxOwners()).isEqualTo(1);
        assertThat(usage.customerCount()).isEqualTo(42L);
        assertThat(usage.maxCustomers()).isEqualTo(100);
        assertThat(usage.supplierCount()).isEqualTo(7L);
        assertThat(usage.maxSuppliers()).isEqualTo(100);
        assertThat(usage.productCount()).isEqualTo(300L);
        assertThat(usage.maxProducts()).isEqualTo(1000);
    }
}
