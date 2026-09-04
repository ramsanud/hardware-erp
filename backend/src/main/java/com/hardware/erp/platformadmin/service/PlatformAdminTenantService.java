package com.hardware.erp.platformadmin.service;

import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.common.dto.PageResponse;
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
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.purchase.repository.PurchaseRepository;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform Admin Console, phase 2 - Tenant Management, the highest-priority
 * module per the spec. Reuses every existing tenant-side entity/repository
 * directly (Tenant, User, Customer, Product, Invoice, Purchase, Payment,
 * BusinessExpense, TenantWhatsAppConnection) - no duplicate tenant model,
 * no shadow "admin view" tables. Every privileged action (suspend/
 * reactivate) is audited via PlatformAuditService with the *acting* admin,
 * never the target.
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminTenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InvoiceRepository invoiceRepository;
    private final PurchaseRepository purchaseRepository;
    private final PaymentRepository paymentRepository;
    private final BusinessExpenseRepository businessExpenseRepository;
    private final TenantWhatsAppConnectionRepository whatsAppConnectionRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<PlatformTenantSummaryResponse> list(String search, TenantStatus status,
                                                              SubscriptionTier tier, Pageable pageable) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.from(
                tenantRepository.search(normalizedSearch, status, tier, pageable),
                this::toSummary);
    }

    @Transactional(readOnly = true)
    public PlatformTenantDetailResponse get(Long tenantId) {
        Tenant tenant = requireTenant(tenantId);
        return toDetail(tenant);
    }

    @Transactional
    public PlatformTenantSummaryResponse suspend(Long tenantId, String reason, Long actingAdminId,
                                                  HttpServletRequest request) {
        Tenant tenant = requireTenant(tenantId);
        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new BusinessException("This tenant is already suspended.");
        }
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);

        PlatformAdmin actingAdmin = platformAdminRepository.getReferenceById(actingAdminId);
        auditService.record(PlatformAuditAction.TENANT_SUSPENDED, actingAdmin, true,
                "TENANT", tenant.getId(), reason, request);

        return toSummary(tenant);
    }

    @Transactional
    public PlatformTenantSummaryResponse reactivate(Long tenantId, Long actingAdminId, HttpServletRequest request) {
        Tenant tenant = requireTenant(tenantId);
        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            throw new BusinessException("This tenant is already active.");
        }
        tenant.setStatus(TenantStatus.ACTIVE);
        tenantRepository.save(tenant);

        PlatformAdmin actingAdmin = platformAdminRepository.getReferenceById(actingAdminId);
        auditService.record(PlatformAuditAction.TENANT_REACTIVATED, actingAdmin, true,
                "TENANT", tenant.getId(), null, request);

        return toSummary(tenant);
    }

    // ---------------------------------------------------------------

    private Tenant requireTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
    }

    private PlatformTenantSummaryResponse toSummary(Tenant tenant) {
        var owner = userRepository.findFirstByTenantIdAndRole_CodeOrderByIdAsc(tenant.getId(), "OWNER").orElse(null);
        long userCount = userRepository.countByTenantId(tenant.getId());
        var lastActive = userRepository.lastLoginAtForTenant(tenant.getId());

        return new PlatformTenantSummaryResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                owner == null ? null : owner.getFullName(),
                owner == null ? null : owner.getEmail(),
                tenant.getPhone(),
                tenant.getEmail(),
                tenant.getSubscriptionTier(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                lastActive,
                userCount);
    }

    private PlatformTenantDetailResponse toDetail(Tenant tenant) {
        Long tenantId = tenant.getId();
        var owner = userRepository.findFirstByTenantIdAndRole_CodeOrderByIdAsc(tenantId, "OWNER").orElse(null);
        var lastActive = userRepository.lastLoginAtForTenant(tenantId);

        var usage = new PlatformTenantDetailResponse.Usage(
                userRepository.countByTenantIdAndStatus(tenantId, UserStatus.ACTIVE),
                customerRepository.countByTenantId(tenantId),
                productRepository.countByTenantId(tenantId),
                invoiceRepository.countByTenantId(tenantId),
                purchaseRepository.countByTenantId(tenantId),
                paymentRepository.countByTenantId(tenantId),
                businessExpenseRepository.countByTenantId(tenantId));

        String whatsAppStatus = whatsAppConnectionRepository.findByTenantId(tenantId)
                .map(connection -> connection.getConnectionStatus().name())
                .orElse(null);

        return new PlatformTenantDetailResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                owner == null ? null : owner.getFullName(),
                owner == null ? null : owner.getEmail(),
                tenant.getPhone(),
                tenant.getEmail(),
                tenant.getCity(),
                tenant.getStateCode(),
                tenant.getSubscriptionTier(),
                tenant.getSubscriptionTrialExpiresAt(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                lastActive,
                usage,
                whatsAppStatus);
    }
}
