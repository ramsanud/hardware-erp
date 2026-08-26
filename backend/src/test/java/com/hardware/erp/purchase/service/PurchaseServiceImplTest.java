package com.hardware.erp.purchase.service;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.entity.StockMovement;
import com.hardware.erp.inventory.service.StockService;
import com.hardware.erp.invoice.entity.PaymentMethod;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.purchase.dto.PurchaseItemRequest;
import com.hardware.erp.purchase.dto.PurchaseRequest;
import com.hardware.erp.purchase.dto.PurchaseResponse;
import com.hardware.erp.purchase.dto.RecordPurchasePaymentRequest;
import com.hardware.erp.purchase.entity.*;
import com.hardware.erp.purchase.mapper.PurchaseMapper;
import com.hardware.erp.purchase.repository.PurchaseDocumentRepository;
import com.hardware.erp.purchase.repository.PurchasePaymentRepository;
import com.hardware.erp.purchase.repository.PurchaseRepository;
import com.hardware.erp.purchase.service.impl.PurchaseServiceImpl;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.supplier.entity.Supplier;
import com.hardware.erp.supplier.repository.SupplierRepository;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseServiceImplTest {

    @Mock private PurchaseRepository purchaseRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private PurchasePaymentRepository purchasePaymentRepository;
    @Mock private PurchaseDocumentRepository purchaseDocumentRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StockService stockService;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;

    @Spy private PurchaseMapper purchaseMapper = new PurchaseMapper();

    @InjectMocks private PurchaseServiceImpl purchaseService;

    private Tenant tenant;
    private Product product;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).slug("default").name("Default")
                .status(TenantStatus.ACTIVE).build();

        product = Product.builder().id(2L).tenant(tenant)
                .productCode("PRD-000002").productName("Test Hammer 500g")
                .unit("PCS").gstRatePercent(new BigDecimal("18.00"))
                .purchasePricePaise(10000L).sellingPricePaise(15000L).mrpPaise(18000L)
                .status(ProductStatus.ACTIVE).build();

        supplier = Supplier.builder().id(3L).tenant(tenant)
                .supplierCode("SUP-0001").supplierName("Godrej Distributors")
                .mobileNo("9823456781").build();

        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(productRepository.findByIdAndTenantId(2L, 1L)).thenReturn(Optional.of(product));
        when(supplierRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(supplier));
        when(documentSequenceService.next(DocumentType.PURCHASE, 1L)).thenReturn("PUR-000001");
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(i -> {
            Purchase p = i.getArgument(0);
            if (p.getId() == null) p.setId(99L);
            return p;
        });
        when(purchasePaymentRepository.save(any(PurchasePayment.class))).thenAnswer(i -> {
            PurchasePayment p = i.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return p;
        });
        when(purchasePaymentRepository.findByPurchaseIdOrderByPaymentDateDesc(anyLong())).thenReturn(List.of());
        when(purchaseDocumentRepository.findByPurchaseIdAndTenantId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(stockService.applyMovement(anyLong(), any(BigDecimal.class), any(MovementType.class),
                anyString(), any(), any()))
                .thenReturn(StockMovement.builder().id(1L).build());

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

    // 5 x purchasePricePaise given in the request (10000 = 100.00) = 500.00 subtotal, 18% GST = 90.00, total = 590.00 (59000 paise)
    private PurchaseRequest request(boolean updateCost, Long initialPayment, PaymentMethod method) {
        return new PurchaseRequest(3L, "BILL-1045", LocalDate.now(),
                List.of(new PurchaseItemRequest(2L, new BigDecimal("5"), 10000L, new BigDecimal("18.00"))),
                updateCost, initialPayment, method, "Test purchase");
    }

    @Test
    @DisplayName("creating a purchase computes subtotal/GST/total correctly and leaves it RECEIVED with no payment")
    void createsAPurchaseWithRealArithmetic() {
        PurchaseResponse response = purchaseService.create(request(false, null, null));

        assertThat(response.subtotalDisplay()).isEqualTo("500.00");
        assertThat(response.totalDisplay()).isEqualTo("590.00");
        assertThat(response.paidDisplay()).isEqualTo("0.00");
        assertThat(response.balanceDisplay()).isEqualTo("590.00");
        assertThat(response.status()).isEqualTo(PurchaseStatus.RECEIVED);
    }

    @Test
    @DisplayName("creating a purchase increases stock via StockService, never a direct mutation")
    void createIncreasesStockThroughStockService() {
        purchaseService.create(request(false, null, null));

        verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("5")),
                eq(MovementType.PURCHASE_RECEIPT), eq("PURCHASE"), eq(99L), isNull());
    }

    @Test
    @DisplayName("updateProductCost=true overwrites the product's purchase price with this bill's price")
    void updateProductCostTrueOverwritesPrice() {
        product.setPurchasePricePaise(8000L); // was 80.00, this bill's price is 100.00 (request()'s item price)

        purchaseService.create(request(true, null, null));

        assertThat(product.getPurchasePricePaise()).isEqualTo(10000L);
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("updateProductCost=false never touches the product's own purchase price")
    void updateProductCostFalseLeavesPriceUntouched() {
        product.setPurchasePricePaise(8000L);

        purchaseService.create(request(false, null, null));

        assertThat(product.getPurchasePricePaise()).isEqualTo(8000L);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("an initial payment above the total is rejected, not silently capped")
    void initialPaymentAboveTotalRejected() {
        assertThatThrownBy(() -> purchaseService.create(request(false, 100000L, PaymentMethod.CASH)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_EXCEEDS_TOTAL");

        verify(stockService, never()).applyMovement(anyLong(), any(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("a partial initial payment gives PARTIALLY_PAID with the correct remaining balance")
    void partialInitialPayment() {
        PurchaseResponse response = purchaseService.create(request(false, 20000L, PaymentMethod.CASH));

        assertThat(response.paidDisplay()).isEqualTo("200.00");
        assertThat(response.balanceDisplay()).isEqualTo("390.00");
        assertThat(response.status()).isEqualTo(PurchaseStatus.PARTIALLY_PAID);
    }

    @Test
    @DisplayName("addPayment reaching the total marks the purchase PAID")
    void addPaymentReachingTotalMarksPaid() {
        Purchase existing = Purchase.builder().id(10L).tenant(tenant).supplier(supplier)
                .purchaseNumber("PUR-000001").purchaseDate(LocalDate.now())
                .subtotalPaise(50000L).gstAmountPaise(9000L).totalPaise(59000L)
                .paidPaise(0L).balancePaise(59000L).status(PurchaseStatus.RECEIVED)
                .items(new java.util.ArrayList<>()).build();
        when(purchaseRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(existing));

        PurchaseResponse response = purchaseService.addPayment(10L,
                new RecordPurchasePaymentRequest(59000L, PaymentMethod.UPI, "Full settlement"));

        assertThat(response.status()).isEqualTo(PurchaseStatus.PAID);
        assertThat(response.balanceDisplay()).isEqualTo("0.00");
    }

    @Test
    @DisplayName("addPayment exceeding the remaining balance is rejected")
    void addPaymentExceedingTotalRejected() {
        Purchase existing = Purchase.builder().id(10L).tenant(tenant).supplier(supplier)
                .purchaseNumber("PUR-000001").purchaseDate(LocalDate.now())
                .subtotalPaise(50000L).gstAmountPaise(9000L).totalPaise(59000L)
                .paidPaise(30000L).balancePaise(29000L).status(PurchaseStatus.PARTIALLY_PAID)
                .items(new java.util.ArrayList<>()).build();
        when(purchaseRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> purchaseService.addPayment(10L,
                new RecordPurchasePaymentRequest(40000L, PaymentMethod.CASH, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_EXCEEDS_TOTAL");
    }

    @Test
    @DisplayName("cancelling a purchase reverses stock with PURCHASE_RETURN and sets status CANCELLED")
    void cancelReversesStock() {
        Purchase existing = Purchase.builder().id(99L).tenant(tenant).supplier(supplier)
                .purchaseNumber("PUR-000001").purchaseDate(LocalDate.now())
                .subtotalPaise(50000L).gstAmountPaise(9000L).totalPaise(59000L)
                .paidPaise(0L).balancePaise(59000L).status(PurchaseStatus.RECEIVED)
                .items(new java.util.ArrayList<>(List.of(
                        PurchaseItem.builder().id(1L).product(product)
                                .productNameSnapshot(product.getProductName())
                                .quantity(new BigDecimal("5")).unit("PCS")
                                .unitPricePaise(10000L).gstRatePercent(new BigDecimal("18.00"))
                                .lineSubtotalPaise(50000L).lineGstPaise(9000L).lineTotalPaise(59000L)
                                .build())))
                .build();
        when(purchaseRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.of(existing));

        PurchaseResponse response = purchaseService.cancel(99L);

        assertThat(response.status()).isEqualTo(PurchaseStatus.CANCELLED);
        verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("-5")),
                eq(MovementType.PURCHASE_RETURN), eq("PURCHASE"), eq(99L), anyString());
    }

    @Test
    @DisplayName("cancelling an already-cancelled purchase is rejected")
    void cancelAlreadyCancelledRejected() {
        Purchase existing = Purchase.builder().id(99L).tenant(tenant).supplier(supplier)
                .purchaseNumber("PUR-000001").purchaseDate(LocalDate.now())
                .subtotalPaise(50000L).gstAmountPaise(9000L).totalPaise(59000L)
                .paidPaise(0L).balancePaise(59000L).status(PurchaseStatus.CANCELLED)
                .items(new java.util.ArrayList<>()).build();
        when(purchaseRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> purchaseService.cancel(99L)).isInstanceOf(BusinessException.class);
    }
}
