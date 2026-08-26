package com.hardware.erp.coupon.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.coupon.dto.CouponDiscountResult;
import com.hardware.erp.coupon.entity.Coupon;
import com.hardware.erp.coupon.entity.CouponStatus;
import com.hardware.erp.coupon.entity.DiscountType;
import com.hardware.erp.coupon.repository.CouponRepository;
import com.hardware.erp.coupon.service.impl.CouponServiceImpl;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.repository.ProductRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouponServiceImplTest {

    @Mock private CouponRepository couponRepository;
    @Mock private ProductRepository productRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;

    @InjectMocks private CouponServiceImpl service;

    @BeforeEach
    void setUp() {
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

    private Coupon.CouponBuilder baseCoupon() {
        return Coupon.builder().id(10L).code("SAVE10")
                .discountType(DiscountType.PERCENT).discountValue(BigDecimal.TEN)
                .status(CouponStatus.ACTIVE).timesUsed(0).products(new LinkedHashSet<>());
    }

    @Test
    @DisplayName("a 10% coupon on an unrestricted cart discounts every line proportionally, summing exactly to the total")
    void percentDiscountAcrossUnrestrictedCart() {
        when(couponRepository.findByTenantIdAndCodeIgnoreCase(1L, "SAVE10"))
                .thenReturn(Optional.of(baseCoupon().build()));

        CouponDiscountResult result = service.calculateDiscount("SAVE10", Map.of(100L, 30000L, 200L, 70000L));

        assertThat(result.totalDiscountPaise()).isEqualTo(10000L); // 10% of 100000
        long sum = result.discountPaiseByProductId().values().stream().mapToLong(Long::longValue).sum();
        assertThat(sum).isEqualTo(result.totalDiscountPaise());
    }

    @Test
    @DisplayName("a percent discount is capped by maxDiscountPaise")
    void percentDiscountRespectsCap() {
        Coupon coupon = baseCoupon().discountValue(BigDecimal.valueOf(50)).maxDiscountPaise(5000L).build();
        when(couponRepository.findByTenantIdAndCodeIgnoreCase(1L, "SAVE10")).thenReturn(Optional.of(coupon));

        CouponDiscountResult result = service.calculateDiscount("SAVE10", Map.of(100L, 100000L));

        assertThat(result.totalDiscountPaise()).isEqualTo(5000L); // 50% would be 50000, capped to 5000
    }

    @Test
    @DisplayName("a flat discount never exceeds the eligible subtotal")
    void flatDiscountNeverExceedsSubtotal() {
        Coupon coupon = baseCoupon().discountType(DiscountType.FLAT).discountValue(BigDecimal.valueOf(100000)).build();
        when(couponRepository.findByTenantIdAndCodeIgnoreCase(1L, "SAVE10")).thenReturn(Optional.of(coupon));

        CouponDiscountResult result = service.calculateDiscount("SAVE10", Map.of(100L, 5000L));

        assertThat(result.totalDiscountPaise()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("a product-restricted coupon only discounts eligible lines, not the whole cart")
    void restrictedToSpecificProducts() {
        Product eligibleProduct = Product.builder().id(100L).build();
        Coupon coupon = baseCoupon().products(new LinkedHashSet<>(Set.of(eligibleProduct))).build();
        when(couponRepository.findByTenantIdAndCodeIgnoreCase(1L, "SAVE10")).thenReturn(Optional.of(coupon));

        CouponDiscountResult result = service.calculateDiscount("SAVE10", Map.of(100L, 20000L, 200L, 80000L));

        assertThat(result.totalDiscountPaise()).isEqualTo(2000L); // 10% of 20000 only
        assertThat(result.discountPaiseByProductId()).doesNotContainKey(200L);
    }

    @Test
    @DisplayName("a coupon that matches nothing in a restricted cart is rejected, not silently discounting zero")
    void restrictedCouponMatchingNothingIsRejected() {
        Product eligibleProduct = Product.builder().id(999L).build();
        Coupon coupon = baseCoupon().products(new LinkedHashSet<>(Set.of(eligibleProduct))).build();
        when(couponRepository.findByTenantIdAndCodeIgnoreCase(1L, "SAVE10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.calculateDiscount("SAVE10", Map.of(100L, 20000L)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("below the minimum purchase amount, the coupon is rejected")
    void belowMinimumPurchaseRejected() {
        Coupon coupon = baseCoupon().minPurchasePaise(50000L).build();
        when(couponRepository.findByTenantIdAndCodeIgnoreCase(1L, "SAVE10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.calculateDiscount("SAVE10", Map.of(100L, 10000L)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("an expired coupon is rejected")
    void expiredCouponRejected() {
        Coupon coupon = baseCoupon().validUntil(LocalDate.now().minusDays(1)).build();
        when(couponRepository.findByTenantIdAndCodeIgnoreCase(1L, "SAVE10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.calculateDiscount("SAVE10", Map.of(100L, 10000L)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("a coupon already used up to its usage limit is rejected")
    void exhaustedUsageLimitRejected() {
        Coupon coupon = baseCoupon().usageLimit(5).timesUsed(5).build();
        when(couponRepository.findByTenantIdAndCodeIgnoreCase(1L, "SAVE10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.calculateDiscount("SAVE10", Map.of(100L, 10000L)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("an unknown coupon code is rejected with a clear message, not a null pointer")
    void unknownCodeRejected() {
        when(couponRepository.findByTenantIdAndCodeIgnoreCase(1L, "NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculateDiscount("NOPE", Map.of(100L, 10000L)))
                .isInstanceOf(BusinessException.class);
    }
}
