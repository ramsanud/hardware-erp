package com.hardware.erp.tenant.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.service.impl.SubscriptionServiceImpl;
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

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CR-032 - an expired trial (SubscriptionCoupon redemption) must revert to FREE the next time currentTier() is read. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceImplTest {

    @Mock private TenantRepository tenantRepository;

    @InjectMocks private SubscriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        Tenant tenant = Tenant.builder().id(1L).slug("default").name("Default").status(TenantStatus.ACTIVE).build();
        Role role = Role.builder().id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE).permissions(new LinkedHashSet<>()).build();
        User authUser = User.builder().id(1L).tenant(tenant).role(role)
                .fullName("Owner").mobileNo("9999999999").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(authUser), null, List.of()));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("an active (not-yet-expired) trial keeps its granted tier")
    void activeTrialKeepsTier() {
        Tenant tenant = Tenant.builder().id(1L).subscriptionTier(SubscriptionTier.MAX)
                .subscriptionTrialExpiresAt(LocalDateTime.now().plusDays(5)).build();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        assertThat(service.currentTier()).isEqualTo(SubscriptionTier.MAX);
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("an expired trial reverts to FREE and clears the trial expiry, right there on read")
    void expiredTrialRevertsToFree() {
        Tenant tenant = Tenant.builder().id(1L).subscriptionTier(SubscriptionTier.MAX)
                .subscriptionTrialExpiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        SubscriptionTier result = service.currentTier();

        assertThat(result).isEqualTo(SubscriptionTier.FREE);
        assertThat(tenant.getSubscriptionTier()).isEqualTo(SubscriptionTier.FREE);
        assertThat(tenant.getSubscriptionTrialExpiresAt()).isNull();
        verify(tenantRepository).save(tenant);
    }

    @Test
    @DisplayName("a permanent tier (no trial) is unaffected")
    void permanentTierUnaffected() {
        Tenant tenant = Tenant.builder().id(1L).subscriptionTier(SubscriptionTier.PRO)
                .subscriptionTrialExpiresAt(null).build();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        assertThat(service.currentTier()).isEqualTo(SubscriptionTier.PRO);
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("requireTier still rejects below the minimum after an expired trial has reverted the tier")
    void requireTierRejectsAfterRevert() {
        Tenant tenant = Tenant.builder().id(1L).subscriptionTier(SubscriptionTier.MAX)
                .subscriptionTrialExpiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> service.requireTier(SubscriptionTier.MAX))
                .isInstanceOf(BusinessException.class);
    }
}
