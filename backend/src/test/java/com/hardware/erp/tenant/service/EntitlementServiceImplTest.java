package com.hardware.erp.tenant.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.config.DeploymentMode;
import com.hardware.erp.config.DeploymentProperties;
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

    /** CR-059 - the hosted deployment, where tier limits apply. What every pre-CR-059 test assumed. */
    private static final DeploymentProperties CLOUD_DEPLOYMENT =
            new DeploymentProperties(DeploymentMode.CLOUD, "", null);

    /** CR-059 - a client's own Docker install: bought outright, so no caps. */
    private static final DeploymentProperties SELF_HOSTED_DEPLOYMENT =
            new DeploymentProperties(DeploymentMode.SELF_HOSTED, "", null);

    private EntitlementServiceImpl entitlementServiceFor(DeploymentProperties deployment) {
        return new EntitlementServiceImpl(
                deployment, subscriptionService, userRepository, customerRepository, supplierRepository, productRepository);
    }

    @BeforeEach
    void setUp() {
        entitlementService = entitlementServiceFor(CLOUD_DEPLOYMENT);

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
    @DisplayName("CR-059 - a self-hosted install enforces no tier limit, however low the tier")
    void selfHostedIgnoresTierLimits() {
        // The install ships on FREE unless somebody changes it, and there is
        // no checkout to reach on a self-hosted box. Without this, the shop
        // that paid the most would be stopped at its 101st customer and told
        // to "upgrade the plan in Shop Settings" - a dead end.
        var selfHosted = entitlementServiceFor(SELF_HOSTED_DEPLOYMENT);
        when(subscriptionService.currentTier()).thenReturn(SubscriptionTier.FREE);
        when(customerRepository.countByStatusAndTenantId(CustomerStatus.ACTIVE, 1L)).thenReturn(5_000L);
        when(supplierRepository.countByStatusAndTenantId(SupplierStatus.ACTIVE, 1L)).thenReturn(5_000L);
        when(productRepository.countByStatusAndTenantId(ProductStatus.ACTIVE, 1L)).thenReturn(50_000L);
        when(userRepository.countActiveOwners(1L)).thenReturn(5L);

        assertThatCode(() -> {
            selfHosted.requireCanAddCustomer();
            selfHosted.requireCanAddSupplier();
            selfHosted.requireCanAddProduct();
            selfHosted.requireCanAddOwner();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CR-059 - a self-hosted usageSummary reports UNLIMITED, not a cap it will never enforce")
    void selfHostedUsageSummaryReportsUnlimited() {
        var selfHosted = entitlementServiceFor(SELF_HOSTED_DEPLOYMENT);
        when(subscriptionService.currentTier()).thenReturn(SubscriptionTier.FREE);
        when(userRepository.countActiveOwners(1L)).thenReturn(2L);
        when(customerRepository.countByStatusAndTenantId(CustomerStatus.ACTIVE, 1L)).thenReturn(142L);
        when(supplierRepository.countByStatusAndTenantId(SupplierStatus.ACTIVE, 1L)).thenReturn(7L);
        when(productRepository.countByStatusAndTenantId(ProductStatus.ACTIVE, 1L)).thenReturn(300L);

        UsageSummaryResponse usage = selfHosted.usageSummary();

        // Real counts stay real - only the ceilings go away, so the Plan usage
        // card still shows how much data the shop holds.
        assertThat(usage.customerCount()).isEqualTo(142L);
        assertThat(usage.maxCustomers()).isEqualTo(SubscriptionTier.UNLIMITED);
        assertThat(usage.maxSuppliers()).isEqualTo(SubscriptionTier.UNLIMITED);
        assertThat(usage.maxProducts()).isEqualTo(SubscriptionTier.UNLIMITED);
        assertThat(usage.maxOwners()).isEqualTo(SubscriptionTier.UNLIMITED);
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
