package com.hardware.erp.inventory.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.entity.Stock;
import com.hardware.erp.inventory.entity.StockMovement;
import com.hardware.erp.inventory.mapper.StockMapper;
import com.hardware.erp.inventory.repository.StockMovementRepository;
import com.hardware.erp.inventory.repository.StockRepository;
import com.hardware.erp.inventory.service.impl.StockServiceImpl;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regression test for BUG-INV-001, found via live browser testing
 * (2026-08-23): applyMovement() never checked whether a movement would
 * drive quantity_on_hand negative, so a real product ("Hammer - Anti-Slip
 * (1 inch)") ended up at -1 ROLL on hand with no warning anywhere - a
 * physically impossible state for a hardware shop's stock count. Fixed by
 * rejecting any movement whose resulting balance would be negative.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockServiceImplTest {

    @Mock private StockRepository stockRepository;
    @Mock private StockMovementRepository movementRepository;
    @Mock private ProductRepository productRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private StockMapper stockMapper;

    private StockServiceImpl stockService;
    private Tenant tenant;
    private Product product;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).slug("default").name("Default").status(TenantStatus.ACTIVE).build();
        product = Product.builder().id(42L).productCode("PRD-004972").productName("Hammer - Anti-Slip (1 inch)")
                .unit("ROLL").sellingPricePaise(50000L).status(ProductStatus.ACTIVE).build();

        stockService = new StockServiceImpl(stockRepository, movementRepository, productRepository,
                tenantRepository, stockMapper);

        Role role = Role.builder().id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE).permissions(new java.util.LinkedHashSet<>()).build();
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
    @DisplayName("applyMovement rejects a SALE that would drive quantity_on_hand negative")
    void rejectsMovementThatWouldGoNegative() {
        Stock stock = Stock.builder().id(1L).tenant(tenant).product(product)
                .quantityOnHand(BigDecimal.valueOf(3)).build();
        when(stockRepository.lockByTenantIdAndProductId(1L, 42L)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> stockService.applyMovement(
                42L, BigDecimal.valueOf(-5), MovementType.SALE, "INVOICE", 99L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Hammer - Anti-Slip (1 inch)")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    @DisplayName("applyMovement allows a SALE that exactly empties stock to zero")
    void allowsMovementThatExactlyReachesZero() {
        Stock stock = Stock.builder().id(1L).tenant(tenant).product(product)
                .quantityOnHand(BigDecimal.valueOf(5)).build();
        when(stockRepository.lockByTenantIdAndProductId(1L, 42L)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any(Stock.class))).thenAnswer(i -> i.getArgument(0));
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        StockMovement movement = stockService.applyMovement(
                42L, BigDecimal.valueOf(-5), MovementType.SALE, "INVOICE", 99L, null);

        assertThat(movement.getBalanceAfter()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stock.getQuantityOnHand()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
