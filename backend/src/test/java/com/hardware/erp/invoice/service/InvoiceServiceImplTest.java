package com.hardware.erp.invoice.service;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.service.CustomerLookupService;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.entity.StockMovement;
import com.hardware.erp.inventory.service.StockService;
import com.hardware.erp.invoice.dto.InvoiceItemRequest;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.dto.PaymentRequest;
import com.hardware.erp.invoice.entity.*;
import com.hardware.erp.invoice.mapper.InvoiceMapper;
import com.hardware.erp.invoice.pdf.InvoicePdfService;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.invoice.repository.PaymentRepository;
import com.hardware.erp.invoice.service.impl.InvoiceServiceImpl;
import com.hardware.erp.notification.service.NotificationService;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceServiceImplTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CustomerLookupService customerLookupService;
    @Mock private ProductRepository productRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;
    @Mock private StockService stockService;
    @Mock private InvoicePdfService invoicePdfService;
    @Mock private NotificationService notificationService;

    @Spy private InvoiceMapper invoiceMapper = new InvoiceMapper();

    @InjectMocks private InvoiceServiceImpl invoiceService;

    private Tenant tenant;
    private Product product;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).slug("default").name("Default")
                .status(TenantStatus.ACTIVE).build();

        product = Product.builder().id(2L).tenant(tenant)
                .productCode("PRD-000002").productName("Test Hammer 500g")
                .unit("PCS").gstRatePercent(new BigDecimal("18.00"))
                .purchasePricePaise(10000L).sellingPricePaise(15000L).mrpPaise(18000L)
                .status(ProductStatus.ACTIVE).build();

        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(productRepository.findByIdAndTenantId(2L, 1L)).thenReturn(Optional.of(product));
        when(documentSequenceService.next(DocumentType.INVOICE, 1L)).thenReturn("INV-000001");
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> {
            Invoice inv = i.getArgument(0);
            if (inv.getId() == null) inv.setId(99L);
            return inv;
        });
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return p;
        });
        when(paymentRepository.findByInvoiceIdOrderByPaymentDateAsc(anyLong())).thenReturn(List.of());
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

    private InvoiceRequest request(Long initialPaymentPaise,
                                   com.hardware.erp.invoice.entity.PaymentMethod method) {
        return new InvoiceRequest("Ramesh Traders", "9876500001", null, null, null,
                List.of(new InvoiceItemRequest(2L, new BigDecimal("2"))),
                initialPaymentPaise, method, null);
    }

    private Customer newCustomer() {
        return Customer.builder().id(1L).tenant(tenant).customerCode("CUS-0001")
                .customerName("Ramesh Traders").mobileNo("9876500001").build();
    }

    // 2 x 150.00 = 300.00 subtotal, 18% GST = 54.00, total = 354.00 (35400 paise)

    @Test
    @DisplayName("no initial payment leaves the invoice UNPAID with the full balance owed")
    void noInitialPaymentIsUnpaid() {
        when(customerLookupService.findOrCreate(anyString(), anyString(), any(), any(), any(), eq(1L)))
                .thenReturn(newCustomer());

        InvoiceResponse response = invoiceService.create(request(null, null));

        assertThat(response.totalDisplay()).isEqualTo("354.00");
        assertThat(response.paidDisplay()).isEqualTo("0.00");
        assertThat(response.balanceDisplay()).isEqualTo("354.00");
        assertThat(response.status()).isEqualTo(InvoiceStatus.UNPAID);
    }

    @Test
    @DisplayName("a partial initial payment gives PARTIALLY_PAID with the correct remaining balance")
    void partialInitialPayment() {
        when(customerLookupService.findOrCreate(anyString(), anyString(), any(), any(), any(), eq(1L)))
                .thenReturn(newCustomer());

        InvoiceResponse response = invoiceService.create(
                request(10000L, com.hardware.erp.invoice.entity.PaymentMethod.CASH));

        assertThat(response.paidDisplay()).isEqualTo("100.00");
        assertThat(response.balanceDisplay()).isEqualTo("254.00");
        assertThat(response.status()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    }

    @Test
    @DisplayName("an initial payment equal to the total gives PAID with zero balance")
    void exactInitialPayment() {
        when(customerLookupService.findOrCreate(anyString(), anyString(), any(), any(), any(), eq(1L)))
                .thenReturn(newCustomer());

        InvoiceResponse response = invoiceService.create(
                request(35400L, com.hardware.erp.invoice.entity.PaymentMethod.UPI));

        assertThat(response.balanceDisplay()).isEqualTo("0.00");
        assertThat(response.status()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    @DisplayName("an initial payment above the total is rejected, not silently capped")
    void initialPaymentAboveTotalRejected() {
        when(customerLookupService.findOrCreate(anyString(), anyString(), any(), any(), any(), eq(1L)))
                .thenReturn(newCustomer());

        assertThatThrownBy(() -> invoiceService.create(
                request(100000L, com.hardware.erp.invoice.entity.PaymentMethod.CASH)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_EXCEEDS_TOTAL");

        verify(stockService, never()).applyMovement(anyLong(), any(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("creating an invoice decrements stock by the sold quantity")
    void createDecrementsStock() {
        when(customerLookupService.findOrCreate(anyString(), anyString(), any(), any(), any(), eq(1L)))
                .thenReturn(newCustomer());

        invoiceService.create(request(null, null));

        verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("-2")),
                eq(MovementType.SALE), eq("INVOICE"), eq(99L), isNull());
    }

    @Test
    @DisplayName("the invoice is linked to whatever customer the lookup service returns")
    void usesCustomerFromLookupService() {
        Customer existing = newCustomer();
        when(customerLookupService.findOrCreate(anyString(), anyString(), any(), any(), any(), eq(1L)))
                .thenReturn(existing);

        InvoiceResponse response = invoiceService.create(request(null, null));

        assertThat(response.customerId()).isEqualTo(existing.getId());
        verify(customerLookupService).findOrCreate(
                "Ramesh Traders", "9876500001", null, null, null, 1L);
    }

    @Test
    @DisplayName("recording a payment that reaches the total moves the invoice to PAID")
    void addPaymentReachingTotalMarksPaid() {
        Invoice invoice = Invoice.builder().id(1L).tenant(tenant).invoiceNumber("INV-000001")
                .customer(newCustomer()).invoiceDate(java.time.LocalDate.now())
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .paidPaise(10000L).balancePaise(25400L).status(InvoiceStatus.PARTIALLY_PAID)
                .build();
        when(invoiceRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(invoice));

        InvoiceResponse response = invoiceService.addPayment(1L,
                new PaymentRequest(25400L, com.hardware.erp.invoice.entity.PaymentMethod.UPI, null));

        assertThat(response.balanceDisplay()).isEqualTo("0.00");
        assertThat(response.status()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    @DisplayName("a payment that would exceed the total is rejected")
    void addPaymentExceedingTotalRejected() {
        Invoice invoice = Invoice.builder().id(1L).tenant(tenant).invoiceNumber("INV-000001")
                .customer(newCustomer()).invoiceDate(java.time.LocalDate.now())
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .paidPaise(10000L).balancePaise(25400L).status(InvoiceStatus.PARTIALLY_PAID)
                .build();
        when(invoiceRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.addPayment(1L,
                new PaymentRequest(30000L, com.hardware.erp.invoice.entity.PaymentMethod.CASH, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_EXCEEDS_TOTAL");

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("cancelling an invoice restores the stock it took")
    void cancelRestoresStock() {
        InvoiceItem item = InvoiceItem.builder().id(1L).product(product)
                .productNameSnapshot(product.getProductName()).quantity(new BigDecimal("2"))
                .unitPricePaise(15000L).gstRatePercent(new BigDecimal("18.00"))
                .lineSubtotalPaise(30000L).lineGstPaise(5400L).lineTotalPaise(35400L).build();
        Invoice invoice = Invoice.builder().id(1L).tenant(tenant).invoiceNumber("INV-000001")
                .customer(newCustomer()).invoiceDate(java.time.LocalDate.now())
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .paidPaise(0L).balancePaise(35400L).status(InvoiceStatus.UNPAID)
                .items(new java.util.ArrayList<>(List.of(item))).build();
        when(invoiceRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(invoice));

        InvoiceResponse response = invoiceService.cancel(1L);

        assertThat(response.status()).isEqualTo(InvoiceStatus.CANCELLED);
        verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("2")),
                eq(MovementType.SALE_REVERSAL), eq("INVOICE"), eq(1L), anyString());
    }

    @Test
    @DisplayName("a cancelled invoice cannot be cancelled again")
    void cannotCancelTwice() {
        Invoice invoice = Invoice.builder().id(1L).tenant(tenant).invoiceNumber("INV-000001")
                .customer(newCustomer()).invoiceDate(java.time.LocalDate.now())
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .paidPaise(0L).balancePaise(35400L).status(InvoiceStatus.CANCELLED)
                .build();
        when(invoiceRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.cancel(1L))
                .isInstanceOf(BusinessException.class);
    }

    // ---- amending an unpaid invoice (CR-039) ----

    private Invoice existingUnpaidInvoice() {
        Customer customer = newCustomer();
        Invoice invoice = Invoice.builder().id(99L).tenant(tenant)
                .invoiceNumber("INV-000001").customer(customer)
                .invoiceDate(java.time.LocalDate.now())
                .status(com.hardware.erp.invoice.entity.InvoiceStatus.UNPAID)
                .paidPaise(0L).build();
        InvoiceItem line = InvoiceItem.builder()
                .invoice(invoice).product(product).quantity(new BigDecimal("2"))
                .unitPricePaise(15000L).gstRatePercent(new BigDecimal("18.00"))
                .lineSubtotalPaise(30000L).lineGstPaise(5400L).lineTotalPaise(35400L)
                .build();
        invoice.getItems().add(line);
        invoice.setSubtotalPaise(30000L);
        invoice.setGstAmountPaise(5400L);
        invoice.setTotalPaise(35400L);
        return invoice;
    }

    @Test
    @DisplayName("adding a line to an unpaid invoice keeps its number and takes only the extra stock")
    void amendTakesOnlyTheDeltaOfStock() {
        Invoice existing = existingUnpaidInvoice();
        when(invoiceRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.of(existing));
        when(customerLookupService.findOrCreate(anyString(), anyString(), any(), any(), any(), eq(1L)))
                .thenReturn(newCustomer());

        // Was 2, now 5 - only 3 more units should leave stock.
        InvoiceRequest amended = new InvoiceRequest("Ramesh Traders", "9876500001", null, null, null,
                List.of(new InvoiceItemRequest(2L, new BigDecimal("5"))), null, null, null);

        InvoiceResponse response = invoiceService.update(99L, amended);

        assertThat(response.invoiceNumber()).isEqualTo("INV-000001");
        org.mockito.ArgumentCaptor<BigDecimal> qty = org.mockito.ArgumentCaptor.forClass(BigDecimal.class);
        verify(stockService).applyMovement(eq(2L), qty.capture(), eq(MovementType.SALE),
                eq("INVOICE"), eq(99L), anyString());
        assertThat(qty.getValue()).isEqualByComparingTo(new BigDecimal("-3"));
    }

    @Test
    @DisplayName("reducing a line on an unpaid invoice returns the difference to stock")
    void amendReturnsStockWhenQuantityDrops() {
        Invoice existing = existingUnpaidInvoice();
        when(invoiceRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.of(existing));
        when(customerLookupService.findOrCreate(anyString(), anyString(), any(), any(), any(), eq(1L)))
                .thenReturn(newCustomer());

        InvoiceRequest amended = new InvoiceRequest("Ramesh Traders", "9876500001", null, null, null,
                List.of(new InvoiceItemRequest(2L, new BigDecimal("1"))), null, null, null);

        invoiceService.update(99L, amended);

        org.mockito.ArgumentCaptor<BigDecimal> qty = org.mockito.ArgumentCaptor.forClass(BigDecimal.class);
        verify(stockService).applyMovement(eq(2L), qty.capture(), eq(MovementType.SALE_REVERSAL),
                eq("INVOICE"), eq(99L), anyString());
        assertThat(qty.getValue()).isEqualByComparingTo(new BigDecimal("1"));
    }

    @Test
    @DisplayName("an unchanged quantity writes no stock movement at all")
    void amendWithNoQuantityChangeMovesNoStock() {
        Invoice existing = existingUnpaidInvoice();
        when(invoiceRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.of(existing));
        when(customerLookupService.findOrCreate(anyString(), anyString(), any(), any(), any(), eq(1L)))
                .thenReturn(newCustomer());

        InvoiceRequest amended = new InvoiceRequest("Ramesh Traders", "9876500001", null, null, "Updated remark",
                List.of(new InvoiceItemRequest(2L, new BigDecimal("2"))), null, null, null);

        invoiceService.update(99L, amended);

        verify(stockService, never()).applyMovement(anyLong(), any(BigDecimal.class),
                any(MovementType.class), anyString(), any(), any());
    }

    @Test
    @DisplayName("an invoice with a payment recorded refuses to be edited")
    void amendRefusedOncePaid() {
        Invoice paid = existingUnpaidInvoice();
        paid.setPaidPaise(10000L);
        when(invoiceRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.of(paid));

        InvoiceRequest amended = new InvoiceRequest("Ramesh Traders", "9876500001", null, null, null,
                List.of(new InvoiceItemRequest(2L, new BigDecimal("5"))), null, null, null);

        assertThatThrownBy(() -> invoiceService.update(99L, amended))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already has a payment");
        verify(stockService, never()).applyMovement(anyLong(), any(BigDecimal.class),
                any(MovementType.class), anyString(), any(), any());
    }

    @Test
    @DisplayName("a cancelled invoice refuses to be edited")
    void amendRefusedWhenCancelled() {
        Invoice cancelled = existingUnpaidInvoice();
        cancelled.setStatus(com.hardware.erp.invoice.entity.InvoiceStatus.CANCELLED);
        when(invoiceRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.of(cancelled));

        InvoiceRequest amended = new InvoiceRequest("Ramesh Traders", "9876500001", null, null, null,
                List.of(new InvoiceItemRequest(2L, new BigDecimal("5"))), null, null, null);

        assertThatThrownBy(() -> invoiceService.update(99L, amended))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cancelled");
    }
}
