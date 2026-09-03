package com.hardware.erp.tenant.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.billing.config.EffectiveRazorpayConfig;
import com.hardware.erp.billing.service.RazorpayConfigResolver;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.dto.TenantSettingsRequest;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantLogoRepository;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.repository.TenantSignatureRepository;
import com.hardware.erp.tenant.repository.TenantUpiQrRepository;
import com.hardware.erp.tenant.service.impl.TenantSettingsServiceImpl;
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

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * CR-057 phase 9 gap fix: once real Razorpay billing exists, the CR-027
 * self-declared plan picker on PUT /v1/settings must not remain a free
 * bypass of checkout for a paid tier. Downgrades (including to FREE) must
 * stay self-service in both cases - see TenantSettingsServiceImpl.update().
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantSettingsServiceImplTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantLogoRepository logoRepository;
    @Mock private TenantSignatureRepository signatureRepository;
    @Mock private TenantUpiQrRepository upiQrRepository;
    @Mock private ActivityLogService activityLog;
    @Mock private SubscriptionService subscriptionService;
    @Mock private RazorpayConfigResolver razorpayConfigResolver;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).slug("default").name("Default")
                .status(TenantStatus.ACTIVE).subscriptionTier(SubscriptionTier.FREE).build();
        Role role = Role.builder().id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE).permissions(new LinkedHashSet<>()).build();
        User authUser = User.builder().id(1L).tenant(tenant).role(role)
                .fullName("Owner").mobileNo("9999999999").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(authUser), null, List.of()));

        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(tenantRepository.save(org.mockito.ArgumentMatchers.any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private TenantSettingsServiceImpl serviceWithRazorpay(boolean active) {
        EffectiveRazorpayConfig config = active
                ? new EffectiveRazorpayConfig(true, "key", "secret", true, "whsec", "https://api.razorpay.com/v1", 99_900L, 299_900L)
                : new EffectiveRazorpayConfig(false, "", "", false, "", "https://api.razorpay.com/v1", 99_900L, 299_900L);
        when(razorpayConfigResolver.resolve()).thenReturn(config);
        return new TenantSettingsServiceImpl(
                tenantRepository, logoRepository, signatureRepository, upiQrRepository,
                activityLog, subscriptionService, razorpayConfigResolver);
    }

    private TenantSettingsRequest requestWithTier(SubscriptionTier tier) {
        return new TenantSettingsRequest(
                "Default Shop", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                tier, null,
                false, false, false, false, false, false, "",
                false, "", BigDecimal.ZERO, false, "", BigDecimal.ZERO,
                false, false, false);
    }

    @Test
    @DisplayName("billing configured: self-declaring an upgrade is rejected - must go through checkout")
    void upgradeRejectedWhenBillingActive() {
        TenantSettingsServiceImpl service = serviceWithRazorpay(true);

        assertThatThrownBy(() -> service.update(requestWithTier(SubscriptionTier.PRO)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("UPGRADE_REQUIRES_CHECKOUT"));
        assertThat(tenant.getSubscriptionTier()).isEqualTo(SubscriptionTier.FREE);
    }

    @Test
    @DisplayName("billing configured: self-declaring a downgrade still works, no checkout needed")
    void downgradeStillAllowedWhenBillingActive() {
        tenant.setSubscriptionTier(SubscriptionTier.MAX);
        TenantSettingsServiceImpl service = serviceWithRazorpay(true);

        service.update(requestWithTier(SubscriptionTier.FREE));

        assertThat(tenant.getSubscriptionTier()).isEqualTo(SubscriptionTier.FREE);
    }

    @Test
    @DisplayName("billing not configured: self-declared upgrade still works exactly as before CR-057 phase 9")
    void upgradeStillAllowedWhenBillingNotConfigured() {
        TenantSettingsServiceImpl service = serviceWithRazorpay(false);

        service.update(requestWithTier(SubscriptionTier.MAX));

        assertThat(tenant.getSubscriptionTier()).isEqualTo(SubscriptionTier.MAX);
    }
}
