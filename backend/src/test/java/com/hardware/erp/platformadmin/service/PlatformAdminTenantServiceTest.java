package com.hardware.erp.platformadmin.service;

import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.customer.repository.CustomerRepository;
import com.hardware.erp.expense.repository.BusinessExpenseRepository;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.invoice.repository.PaymentRepository;
import com.hardware.erp.notification.repository.TenantWhatsAppConnectionRepository;
import com.hardware.erp.platformadmin.dto.PlatformTenantDetailResponse;
import com.hardware.erp.platformadmin.dto.PlatformTenantSummaryResponse;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAdminRole;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.purchase.repository.PurchaseRepository;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformAdminTenantServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PurchaseRepository purchaseRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private BusinessExpenseRepository businessExpenseRepository;
    @Mock private TenantWhatsAppConnectionRepository whatsAppConnectionRepository;
    @Mock private PlatformAdminRepository platformAdminRepository;
    @Mock private PlatformAuditService auditService;

    private PlatformAdminTenantService service;
    private PlatformAdmin actingAdmin;

    @BeforeEach
    void setUp() {
        service = new PlatformAdminTenantService(
                tenantRepository, userRepository, customerRepository, productRepository,
                invoiceRepository, purchaseRepository, paymentRepository, businessExpenseRepository,
                whatsAppConnectionRepository, platformAdminRepository, auditService);

        actingAdmin = PlatformAdmin.builder().id(99L).fullName("Acting Admin")
                .email("acting@platform.test").role(PlatformAdminRole.SUPPORT_ADMIN).build();
        when(platformAdminRepository.getReferenceById(99L)).thenReturn(actingAdmin);
        when(whatsAppConnectionRepository.findByTenantId(any())).thenReturn(Optional.empty());
    }

    private Tenant tenant(Long id, TenantStatus status) {
        return Tenant.builder().id(id).slug("t" + id).name("Tenant " + id).status(status).build();
    }

    @Test
    @DisplayName("suspending an active tenant flips its status and audits the ACTING admin, not the target")
    void suspendFlipsStatusAndAuditsActingAdmin() {
        Tenant tenant = tenant(5L, TenantStatus.ACTIVE);
        when(tenantRepository.findById(5L)).thenReturn(Optional.of(tenant));
        when(userRepository.findFirstByTenantIdAndRole_CodeOrderByIdAsc(5L, "OWNER")).thenReturn(Optional.empty());

        HttpServletRequest request = mock(HttpServletRequest.class);
        PlatformTenantSummaryResponse result = service.suspend(5L, "Payment issue", 99L, request);

        assertThat(result.status()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        verify(tenantRepository).save(tenant);

        ArgumentCaptor<PlatformAdmin> adminCaptor = ArgumentCaptor.forClass(PlatformAdmin.class);
        verify(auditService).record(eq(PlatformAuditAction.TENANT_SUSPENDED), adminCaptor.capture(),
                eq(true), eq("TENANT"), eq(5L), eq("Payment issue"), eq(request));
        assertThat(adminCaptor.getValue().getId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("suspending an already-suspended tenant is refused, not a silent no-op")
    void suspendAlreadySuspendedThrows() {
        Tenant tenant = tenant(6L, TenantStatus.SUSPENDED);
        when(tenantRepository.findById(6L)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> service.suspend(6L, "reason", 99L, mock(HttpServletRequest.class)))
                .isInstanceOf(BusinessException.class);
        verify(auditService, never()).record(any(), any(), anyBoolean(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("reactivating an already-active tenant is refused")
    void reactivateAlreadyActiveThrows() {
        Tenant tenant = tenant(7L, TenantStatus.ACTIVE);
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> service.reactivate(7L, 99L, mock(HttpServletRequest.class)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("an unknown tenant id is a real not-found, never a null result")
    void unknownTenantThrowsNotFound() {
        when(tenantRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("tenant detail aggregates real usage counts from every module's own repository")
    void detailAggregatesUsageCounts() {
        Tenant tenant = tenant(8L, TenantStatus.ACTIVE);
        when(tenantRepository.findById(8L)).thenReturn(Optional.of(tenant));
        when(userRepository.findFirstByTenantIdAndRole_CodeOrderByIdAsc(8L, "OWNER")).thenReturn(Optional.empty());
        when(userRepository.countByTenantIdAndStatus(8L, UserStatus.ACTIVE)).thenReturn(3L);
        when(customerRepository.countByTenantId(8L)).thenReturn(10L);
        when(productRepository.countByTenantId(8L)).thenReturn(20L);
        when(invoiceRepository.countByTenantId(8L)).thenReturn(30L);
        when(purchaseRepository.countByTenantId(8L)).thenReturn(5L);
        when(paymentRepository.countByTenantId(8L)).thenReturn(25L);
        when(businessExpenseRepository.countByTenantId(8L)).thenReturn(2L);

        PlatformTenantDetailResponse detail = service.get(8L);

        assertThat(detail.usage()).isEqualTo(new PlatformTenantDetailResponse.Usage(3, 10, 20, 30, 5, 25, 2));
        assertThat(detail.whatsAppConnectionStatus()).isNull();
    }
}
