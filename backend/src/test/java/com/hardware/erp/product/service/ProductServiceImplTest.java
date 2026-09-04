package com.hardware.erp.product.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.activity.ActivityAction;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.mapper.ProductMapper;
import com.hardware.erp.product.repository.BrandRepository;
import com.hardware.erp.product.repository.CategoryRepository;
import com.hardware.erp.product.repository.ProductImageRepository;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.product.service.impl.ProductServiceImpl;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CR-058. ProductServiceImpl had no unit test at all before this; this covers
 * the soft-delete recovery paths added by that CR rather than retrofitting
 * coverage for the whole service, which is its own piece of work.
 *
 * The authorisation check for restore lives entirely in
 * ProductRepository.restoreDeleted's WHERE clause, so these tests assert on
 * what the service does with its row count: 1 means the guarded update
 * matched, 0 means it did not match for ANY reason (another tenant's row, a
 * row that was never deleted, an id that does not exist) - and all of those
 * must surface identically as 404 so a caller cannot probe ids.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private ProductMapper productMapper;
    @Mock private ActivityLogService activityLog;
    @Mock private TenantRepository tenantRepository;
    @Mock private com.hardware.erp.tenant.service.EntitlementService entitlementService;
    @Mock private com.hardware.erp.invoice.repository.InvoiceItemRepository invoiceItemRepository;

    @InjectMocks private ProductServiceImpl productService;

    private Product existing;

    @BeforeEach
    void setUp() {
        existing = Product.builder()
                .id(42L)
                .productCode("PRD-000042")
                .productName("Godrej 6-lever Padlock 65mm")
                .unit("PCS")
                .status(ProductStatus.ACTIVE)
                .build();

        when(productRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(existing));

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
    @DisplayName("a genuinely deleted product is restored in place, keeping its id, and is audit-logged")
    void restoresDeletedProduct() {
        when(productRepository.restoreDeleted(42L, 1L)).thenReturn(1);

        productService.restore(42L);

        // The tenant id comes from the security context, never the caller.
        verify(productRepository).restoreDeleted(42L, 1L);
        // Restored in place: no new row is ever created on this path, so the
        // product keeps its id, its code, its stock row and every invoice line.
        verify(productRepository, never()).save(argThat(p -> p.getId() == null));
        verify(activityLog).action(eq("PRODUCT"), eq("PRODUCT"), eq(42L),
                eq("Godrej 6-lever Padlock 65mm"), eq(ActivityAction.RESTORE), anyString());
    }

    @Test
    @DisplayName("a product that was never deleted gives 404 and writes no audit entry")
    void notDeletedGives404() {
        when(productRepository.restoreDeleted(42L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> productService.restore(42L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(activityLog);
    }

    @Test
    @DisplayName("a nonexistent id gives 404, indistinguishable from the not-deleted case")
    void unknownIdGives404() {
        when(productRepository.restoreDeleted(4242L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> productService.restore(4242L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(activityLog);
    }

    @Test
    @DisplayName("another tenant's deleted product cannot be restored")
    void cannotRestoreAnotherTenantsProduct() {
        // The signed-in principal is tenant 1. Product 42 here belongs to
        // tenant 2, so the guarded UPDATE matches nothing.
        when(productRepository.restoreDeleted(42L, 1L)).thenReturn(0);
        when(productRepository.restoreDeleted(42L, 2L)).thenReturn(1);

        assertThatThrownBy(() -> productService.restore(42L))
                .isInstanceOf(ResourceNotFoundException.class);

        // No caller-supplied value can redirect the update at another shop.
        verify(productRepository, never()).restoreDeleted(42L, 2L);
        verifyNoInteractions(activityLog);
    }

    @Test
    @DisplayName("listDeleted only ever asks for the caller's own tenant")
    void listDeletedIsTenantScoped() {
        when(productRepository.findDeletedByTenantId(1L)).thenReturn(List.of());

        assertThat(productService.listDeleted()).isEmpty();

        verify(productRepository).findDeletedByTenantId(1L);
        verify(productRepository, never()).findDeletedByTenantId(2L);
    }
}
