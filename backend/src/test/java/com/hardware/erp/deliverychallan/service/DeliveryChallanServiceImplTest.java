package com.hardware.erp.deliverychallan.service;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.idempotency.IdempotencyService;
import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.service.CustomerLookupService;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanItemRequest;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanRequest;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanResponse;
import com.hardware.erp.deliverychallan.entity.DeliveryChallan;
import com.hardware.erp.deliverychallan.entity.DeliveryChallanItem;
import com.hardware.erp.deliverychallan.entity.DeliveryChallanStatus;
import com.hardware.erp.deliverychallan.mapper.DeliveryChallanMapper;
import com.hardware.erp.deliverychallan.repository.DeliveryChallanRepository;
import com.hardware.erp.deliverychallan.service.impl.DeliveryChallanServiceImpl;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.entity.StockMovement;
import com.hardware.erp.inventory.service.StockService;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.service.InvoiceService;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
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
class DeliveryChallanServiceImplTest {

    @Mock private DeliveryChallanRepository deliveryChallanRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private CustomerLookupService customerLookupService;
    @Mock private ProductRepository productRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;
    @Mock private StockService stockService;
    @Mock private InvoiceService invoiceService;
    @Mock private IdempotencyService idempotencyService;

    @Spy private DeliveryChallanMapper deliveryChallanMapper = new DeliveryChallanMapper();

    @InjectMocks private DeliveryChallanServiceImpl deliveryChallanService;

    private Tenant tenant;
    private Product product;
    private Customer customer;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).slug("default").name("Default")
                .status(TenantStatus.ACTIVE).build();
        product = Product.builder().id(2L).tenant(tenant)
                .productCode("PRD-000002").productName("Test Hammer 500g")
                .unit("PCS").gstRatePercent(new BigDecimal("18.00"))
                .purchasePricePaise(10000L).sellingPricePaise(15000L).mrpPaise(18000L)
                .status(ProductStatus.ACTIVE).build();
        customer = Customer.builder().id(3L).tenant(tenant).customerCode("CUS-0001")
                .customerName("Ramesh Traders").mobileNo("9876500001").build();

        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(productRepository.findByIdAndTenantId(2L, 1L)).thenReturn(Optional.of(product));
        when(documentSequenceService.next(DocumentType.DELIVERY_CHALLAN, 1L)).thenReturn("DC-000001");
        when(customerLookupService.findOrCreate(anyString(), anyString(), any(), any(), any(), eq(1L)))
                .thenReturn(customer);
        when(deliveryChallanRepository.save(any(DeliveryChallan.class))).thenAnswer(i -> {
            DeliveryChallan c = i.getArgument(0);
            if (c.getId() == null) c.setId(9L);
            return c;
        });
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

    private DeliveryChallanRequest request() {
        return new DeliveryChallanRequest("Ramesh Traders", "9876500001", null, null, null,
                List.of(new DeliveryChallanItemRequest(2L, new BigDecimal("2"))),
                null, null, null, null);
    }

    @Test
    @DisplayName("issuing a challan decrements stock with a DELIVERY movement and computes the informational value")
    void createDecrementsStock() {
        DeliveryChallanResponse response = deliveryChallanService.create(request(), null);

        assertThat(response.totalValueDisplay()).isEqualTo("300.00");
        assertThat(response.status()).isEqualTo(DeliveryChallanStatus.ISSUED);
        verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("-2")), eq(MovementType.DELIVERY),
                eq("DELIVERY_CHALLAN"), eq(9L), any());
    }

    @Test
    @DisplayName("cancelling an issued challan restores stock with DELIVERY_REVERSAL")
    void cancelRestoresStock() {
        var item = DeliveryChallanItem.builder()
                .id(1L).product(product).productNameSnapshot(product.getProductName())
                .quantity(new BigDecimal("2")).unit("PCS").unitPricePaise(15000L).valuePaise(30000L).build();
        DeliveryChallan challan = DeliveryChallan.builder().id(1L).tenant(tenant)
                .deliveryChallanNumber("DC-000001").customer(customer)
                .challanDate(LocalDate.now()).totalValuePaise(30000L)
                .status(DeliveryChallanStatus.ISSUED)
                .items(new java.util.ArrayList<>(List.of(item))).build();
        when(deliveryChallanRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(challan));

        DeliveryChallanResponse response = deliveryChallanService.cancel(1L);

        assertThat(response.status()).isEqualTo(DeliveryChallanStatus.CANCELLED);
        verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("2")), eq(MovementType.DELIVERY_REVERSAL),
                eq("DELIVERY_CHALLAN"), eq(1L), any());
    }

    @Test
    @DisplayName("a cancelled challan cannot be cancelled again")
    void cancelRejectsAlreadyCancelled() {
        DeliveryChallan challan = DeliveryChallan.builder().id(1L).tenant(tenant)
                .deliveryChallanNumber("DC-000001").customer(customer)
                .challanDate(LocalDate.now()).totalValuePaise(30000L)
                .status(DeliveryChallanStatus.CANCELLED).build();
        when(deliveryChallanRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(challan));

        assertThatThrownBy(() -> deliveryChallanService.cancel(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("converting to invoice reverses the challan's own DELIVERY movement, then bills through InvoiceService")
    void convertReversesDeliveryThenInvoices() {
        var item = DeliveryChallanItem.builder()
                .id(1L).product(product).productNameSnapshot(product.getProductName())
                .quantity(new BigDecimal("2")).unit("PCS").unitPricePaise(15000L).valuePaise(30000L).build();
        DeliveryChallan challan = DeliveryChallan.builder().id(1L).tenant(tenant)
                .deliveryChallanNumber("DC-000001").customer(customer)
                .challanDate(LocalDate.now()).totalValuePaise(30000L)
                .status(DeliveryChallanStatus.ISSUED)
                .items(new java.util.ArrayList<>(List.of(item))).build();
        when(deliveryChallanRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(challan));
        when(invoiceService.create(any(InvoiceRequest.class))).thenReturn(
                new InvoiceResponse(77L, "INV-000077", 3L, "Ramesh Traders", "9876500001",
                        LocalDate.now(), "300.00", "54.00", "354.00", null, null, null, "0.00", "354.00",
                        com.hardware.erp.invoice.entity.InvoiceStatus.UNPAID, null, null, null, null,
                        List.of(), List.of(), null, null, null, null));

        DeliveryChallanResponse response = deliveryChallanService.convertToInvoice(1L, null);

        assertThat(response.status()).isEqualTo(DeliveryChallanStatus.CONVERTED);
        assertThat(response.convertedInvoiceId()).isEqualTo(77L);
        verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("2")), eq(MovementType.DELIVERY_REVERSAL),
                eq("DELIVERY_CHALLAN"), eq(1L), any());
        verify(invoiceService).create(any(InvoiceRequest.class));
    }

    @Test
    @DisplayName("a converted challan cannot be converted again")
    void convertRejectsAlreadyConverted() {
        DeliveryChallan challan = DeliveryChallan.builder().id(1L).tenant(tenant)
                .deliveryChallanNumber("DC-000001").customer(customer)
                .challanDate(LocalDate.now()).totalValuePaise(30000L)
                .status(DeliveryChallanStatus.CONVERTED).build();
        when(deliveryChallanRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(challan));

        assertThatThrownBy(() -> deliveryChallanService.convertToInvoice(1L, null))
                .isInstanceOf(BusinessException.class);
        verify(invoiceService, never()).create(any());
    }
}
