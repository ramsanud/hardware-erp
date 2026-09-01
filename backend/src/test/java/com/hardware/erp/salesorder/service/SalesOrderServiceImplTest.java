package com.hardware.erp.salesorder.service;

import com.hardware.erp.common.idempotency.IdempotencyService;
import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.service.CustomerLookupService;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanRequest;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanResponse;
import com.hardware.erp.deliverychallan.entity.DeliveryChallanStatus;
import com.hardware.erp.deliverychallan.service.DeliveryChallanService;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.service.InvoiceService;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.salesorder.dto.SalesOrderItemRequest;
import com.hardware.erp.salesorder.dto.SalesOrderRequest;
import com.hardware.erp.salesorder.dto.SalesOrderResponse;
import com.hardware.erp.salesorder.entity.SalesOrder;
import com.hardware.erp.salesorder.entity.SalesOrderStatus;
import com.hardware.erp.salesorder.mapper.SalesOrderMapper;
import com.hardware.erp.salesorder.repository.SalesOrderRepository;
import com.hardware.erp.salesorder.service.impl.SalesOrderServiceImpl;
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
class SalesOrderServiceImplTest {

    @Mock private SalesOrderRepository salesOrderRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private CustomerLookupService customerLookupService;
    @Mock private ProductRepository productRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;
    @Mock private InvoiceService invoiceService;
    @Mock private DeliveryChallanService deliveryChallanService;
    @Mock private IdempotencyService idempotencyService;

    @Spy private SalesOrderMapper salesOrderMapper = new SalesOrderMapper();

    @InjectMocks private SalesOrderServiceImpl salesOrderService;

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
        when(documentSequenceService.next(DocumentType.SALES_ORDER, 1L)).thenReturn("SO-000001");
        when(customerLookupService.findOrCreate(anyString(), anyString(), any(), any(), any(), eq(1L)))
                .thenReturn(customer);
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(i -> {
            SalesOrder o = i.getArgument(0);
            if (o.getId() == null) o.setId(9L);
            return o;
        });

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

    private SalesOrderRequest request() {
        return new SalesOrderRequest("Ramesh Traders", "9876500001", null, null, null,
                LocalDate.now().plusDays(7), List.of(new SalesOrderItemRequest(2L, new BigDecimal("2"))),
                null, null, null);
    }

    // 2 x 150.00 = 300.00 subtotal, 18% GST = 54.00, total = 354.00 (35400 paise)

    @Test
    @DisplayName("creating a sales order computes subtotal, GST and total from the current product price")
    void createComputesTotals() {
        SalesOrderResponse response = salesOrderService.create(request(), null);

        assertThat(response.subtotalDisplay()).isEqualTo("300.00");
        assertThat(response.gstAmountDisplay()).isEqualTo("54.00");
        assertThat(response.totalDisplay()).isEqualTo("354.00");
        assertThat(response.status()).isEqualTo(SalesOrderStatus.DRAFT);
        verifyNoInteractions(idempotencyService);
    }

    @Test
    @DisplayName("an idempotency key routes creation through IdempotencyService instead of running twice")
    void createWithIdempotencyKeyDelegatesToIdempotencyService() {
        when(idempotencyService.execute(eq(1L), eq("sales_order.create"), eq("key-1"), any(), eq(SalesOrderResponse.class), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<SalesOrderResponse> action = invocation.getArgument(5);
                    return action.get();
                });

        SalesOrderResponse response = salesOrderService.create(request(), "key-1");

        assertThat(response.totalDisplay()).isEqualTo("354.00");
        verify(idempotencyService).execute(eq(1L), eq("sales_order.create"), eq("key-1"), any(), eq(SalesOrderResponse.class), any());
    }

    @Test
    @DisplayName("a cancelled sales order cannot be converted")
    void convertRejectsCancelledStatus() {
        SalesOrder order = SalesOrder.builder().id(1L).tenant(tenant)
                .salesOrderNumber("SO-000001").customer(customer)
                .orderDate(LocalDate.now())
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(SalesOrderStatus.CANCELLED).build();
        when(salesOrderRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> salesOrderService.convertToInvoice(1L, null))
                .isInstanceOf(BusinessException.class);
        verify(invoiceService, never()).create(any());
    }

    @Test
    @DisplayName("a cancelled sales order cannot be converted to a delivery challan either")
    void convertToDeliveryChallanRejectsCancelledStatus() {
        SalesOrder order = SalesOrder.builder().id(1L).tenant(tenant)
                .salesOrderNumber("SO-000001").customer(customer)
                .orderDate(LocalDate.now())
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(SalesOrderStatus.CANCELLED).build();
        when(salesOrderRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> salesOrderService.convertToDeliveryChallan(1L, null))
                .isInstanceOf(BusinessException.class);
        verify(deliveryChallanService, never()).createFromSalesOrder(any(), any(), any());
    }

    @Test
    @DisplayName("a sales order already converted cannot be converted to a delivery challan a second time")
    void convertToDeliveryChallanRejectsAlreadyConverted() {
        SalesOrder order = SalesOrder.builder().id(1L).tenant(tenant)
                .salesOrderNumber("SO-000001").customer(customer)
                .orderDate(LocalDate.now())
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(SalesOrderStatus.CONVERTED).convertedInvoiceId(77L).build();
        when(salesOrderRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> salesOrderService.convertToDeliveryChallan(1L, null))
                .isInstanceOf(BusinessException.class);
        verify(deliveryChallanService, never()).createFromSalesOrder(any(), any(), any());
    }

    @Test
    @DisplayName("converting a valid sales order creates an invoice through InvoiceService and marks CONVERTED")
    void convertCreatesInvoiceAndMarksConverted() {
        var item = com.hardware.erp.salesorder.entity.SalesOrderItem.builder()
                .id(1L).product(product).productNameSnapshot(product.getProductName())
                .quantity(new BigDecimal("2")).unitPricePaise(15000L).gstRatePercent(new BigDecimal("18.00"))
                .lineSubtotalPaise(30000L).lineGstPaise(5400L).lineTotalPaise(35400L).build();
        SalesOrder order = SalesOrder.builder().id(1L).tenant(tenant)
                .salesOrderNumber("SO-000001").customer(customer)
                .orderDate(LocalDate.now())
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(SalesOrderStatus.CONFIRMED)
                .items(new java.util.ArrayList<>(List.of(item))).build();
        when(salesOrderRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(order));
        when(invoiceService.create(any(InvoiceRequest.class))).thenReturn(
                new InvoiceResponse(77L, "INV-000077", 3L, "Ramesh Traders", "9876500001",
                        LocalDate.now(), "300.00", "54.00", "354.00", null, null, null, "0.00", "354.00",
                        com.hardware.erp.invoice.entity.InvoiceStatus.UNPAID, null, null, null, null,
                        List.of(), List.of(), null, null, null, null));

        SalesOrderResponse response = salesOrderService.convertToInvoice(1L, null);

        assertThat(response.status()).isEqualTo(SalesOrderStatus.CONVERTED);
        assertThat(response.convertedInvoiceId()).isEqualTo(77L);
        verify(invoiceService).create(any(InvoiceRequest.class));
    }

    @Test
    @DisplayName("a converted sales order's status can never be changed again")
    void statusCannotChangeAfterConversion() {
        SalesOrder order = SalesOrder.builder().id(1L).tenant(tenant)
                .salesOrderNumber("SO-000001").customer(customer)
                .orderDate(LocalDate.now())
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(SalesOrderStatus.CONVERTED).convertedInvoiceId(77L).build();
        when(salesOrderRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> salesOrderService.updateStatus(1L, SalesOrderStatus.CANCELLED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("CONVERTED cannot be set directly through the status endpoint")
    void cannotSetConvertedDirectly() {
        SalesOrder order = SalesOrder.builder().id(1L).tenant(tenant)
                .salesOrderNumber("SO-000001").customer(customer)
                .orderDate(LocalDate.now())
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(SalesOrderStatus.DRAFT).build();
        when(salesOrderRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> salesOrderService.updateStatus(1L, SalesOrderStatus.CONVERTED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("converting to a delivery challan carries only product and quantity, and marks CONVERTED")
    void convertToDeliveryChallanCreatesChallanAndMarksConverted() {
        var item = com.hardware.erp.salesorder.entity.SalesOrderItem.builder()
                .id(1L).product(product).productNameSnapshot(product.getProductName())
                .quantity(new BigDecimal("2")).unitPricePaise(15000L).gstRatePercent(new BigDecimal("18.00"))
                .lineSubtotalPaise(30000L).lineGstPaise(5400L).lineTotalPaise(35400L).build();
        SalesOrder order = SalesOrder.builder().id(1L).tenant(tenant)
                .salesOrderNumber("SO-000001").customer(customer)
                .orderDate(LocalDate.now())
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(SalesOrderStatus.CONFIRMED)
                .items(new java.util.ArrayList<>(List.of(item))).build();
        when(salesOrderRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(order));
        when(deliveryChallanService.createFromSalesOrder(any(DeliveryChallanRequest.class), eq(1L), eq(1L)))
                .thenReturn(new DeliveryChallanResponse(55L, "DC-000055", 3L, "Ramesh Traders", "9876500001",
                        LocalDate.now(), null, null, null, "300.00",
                        DeliveryChallanStatus.ISSUED, null, 1L, null, List.of(), null));

        SalesOrderResponse response = salesOrderService.convertToDeliveryChallan(1L, null);

        assertThat(response.status()).isEqualTo(SalesOrderStatus.CONVERTED);
        assertThat(response.convertedDeliveryChallanId()).isEqualTo(55L);
        verify(deliveryChallanService).createFromSalesOrder(any(DeliveryChallanRequest.class), eq(1L), eq(1L));
    }

    @Test
    @DisplayName("editing a converted sales order is rejected")
    void editRejectsConvertedStatus() {
        SalesOrder order = SalesOrder.builder().id(1L).tenant(tenant)
                .salesOrderNumber("SO-000001").customer(customer)
                .orderDate(LocalDate.now())
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(SalesOrderStatus.CONVERTED).build();
        when(salesOrderRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> salesOrderService.update(1L, request()))
                .isInstanceOf(BusinessException.class);
    }
}
