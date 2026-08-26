package com.hardware.erp.tenant.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.dto.SubscriptionCouponRedemptionResponse;
import com.hardware.erp.tenant.entity.SubscriptionCoupon;
import com.hardware.erp.tenant.entity.SubscriptionCouponStatus;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.SubscriptionCouponRepository;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.service.impl.SubscriptionCouponServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CR-032 - subscription trial coupons: the shop's OWNER granting their own tenant a plan for free, for a limited time. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionCouponServiceImplTest {

    @Mock private SubscriptionCouponRepository couponRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;

    @InjectMocks private SubscriptionCouponServiceImpl service;

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
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
        when(couponRepository.save(any(SubscriptionCoupon.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SubscriptionCoupon freeMaxCoupon() {
        return SubscriptionCoupon.builder().id(9L).tenant(tenant).code("WELCOME2026")
                .grantedTier(SubscriptionTier.MAX).trialDays(30)
                .status(SubscriptionCouponStatus.ACTIVE).timesUsed(0).build();
    }

    @Test
    @DisplayName("redeem() grants the coupon's tier and sets a trial expiry trialDays from now")
    void redeemGrantsTierAndTrialExpiry() {
        SubscriptionCoupon coupon = freeMaxCoupon();
        when(couponRepository.findByTenantIdAndCodeIgnoreCase(1L, "WELCOME2026")).thenReturn(Optional.of(coupon));

        SubscriptionCouponRedemptionResponse result = service.redeem("WELCOME2026");

        assertThat(result.grantedTier()).isEqualTo(SubscriptionTier.MAX);
        assertThat(tenant.getSubscriptionTier()).isEqualTo(SubscriptionTier.MAX);
        assertThat(tenant.getSubscriptionTrialExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
        assertThat(tenant.getSubscriptionTrialExpiresAt()).isBefore(LocalDateTime.now().plusDays(31));
        assertThat(coupon.getTimesUsed()).isEqualTo(1);
    }

    @Test
    @DisplayName("redeem() rejects an unknown code")
    void redeemRejectsUnknownCode() {
        when(couponRepository.findByTenantIdAndCodeIgnoreCase(1L, "NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem("NOPE")).isInstanceOf(BusinessException.class);
        verify(tenantRepository, org.mockito.Mockito.never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("redeem() rejects a coupon that has already hit its usage limit")
    void redeemRejectsAtUsageLimit() {
        SubscriptionCoupon coupon = freeMaxCoupon();
        coupon.setUsageLimit(1);
        coupon.setTimesUsed(1);
        when(couponRepository.findByTenantIdAndCodeIgnoreCase(1L, "WELCOME2026")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.redeem("WELCOME2026"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("maximum number of times");
    }

    @Test
    @DisplayName("redeem() rejects an expired coupon")
    void redeemRejectsExpiredCoupon() {
        SubscriptionCoupon coupon = freeMaxCoupon();
        coupon.setValidUntil(LocalDate.now().minusDays(1));
        when(couponRepository.findByTenantIdAndCodeIgnoreCase(1L, "WELCOME2026")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.redeem("WELCOME2026"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("redeem() rejects an INACTIVE coupon")
    void redeemRejectsInactiveCoupon() {
        SubscriptionCoupon coupon = freeMaxCoupon();
        coupon.setStatus(SubscriptionCouponStatus.INACTIVE);
        when(couponRepository.findByTenantIdAndCodeIgnoreCase(1L, "WELCOME2026")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.redeem("WELCOME2026")).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("create() rejects a duplicate code within the same tenant")
    void createRejectsDuplicateCode() {
        when(couponRepository.existsByTenantIdAndCodeIgnoreCase(1L, "WELCOME2026")).thenReturn(true);

        var request = new com.hardware.erp.tenant.dto.SubscriptionCouponRequest(
                "WELCOME2026", null, SubscriptionTier.MAX, 30, null, null, null, SubscriptionCouponStatus.ACTIVE);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(com.hardware.erp.common.exception.DuplicateResourceException.class);
    }

    @Test
    @DisplayName("create() upper-cases the code, matching the retail Coupon convention")
    void createUppercasesCode() {
        var request = new com.hardware.erp.tenant.dto.SubscriptionCouponRequest(
                "welcome2026", "100% free for a month", SubscriptionTier.MAX, 30,
                null, null, null, SubscriptionCouponStatus.ACTIVE);

        ArgumentCaptor<SubscriptionCoupon> captor = ArgumentCaptor.forClass(SubscriptionCoupon.class);
        service.create(request);
        verify(couponRepository).save(captor.capture());

        assertThat(captor.getValue().getCode()).isEqualTo("WELCOME2026");
        assertThat(captor.getValue().getGrantedTier()).isEqualTo(SubscriptionTier.MAX);
        assertThat(captor.getValue().getTrialDays()).isEqualTo(30);
    }
}
