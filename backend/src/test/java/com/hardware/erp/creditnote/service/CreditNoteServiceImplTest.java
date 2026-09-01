package com.hardware.erp.creditnote.service;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.idempotency.IdempotencyService;
import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.creditnote.dto.CreditNoteItemRequest;
import com.hardware.erp.creditnote.dto.CreditNoteRequest;
import com.hardware.erp.creditnote.dto.CreditNoteResponse;
import com.hardware.erp.creditnote.entity.CreditNote;
import com.hardware.erp.creditnote.entity.CreditNoteStatus;
import com.hardware.erp.creditnote.mapper.CreditNoteMapper;
import com.hardware.erp.creditnote.repository.CreditNoteItemRepository;
import com.hardware.erp.creditnote.repository.CreditNoteRepository;
import com.hardware.erp.creditnote.service.impl.CreditNoteServiceImpl;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.entity.StockMovement;
import com.hardware.erp.inventory.service.StockService;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.entity.InvoiceItem;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreditNoteServiceImplTest {

    @Mock private CreditNoteRepository creditNoteRepository;
    @Mock private CreditNoteItemRepository creditNoteItemRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;
    @Mock private StockService stockService;
    @Mock private IdempotencyService idempotencyService;

    @Spy private CreditNoteMapper creditNoteMapper = new CreditNoteMapper();

    @InjectMocks private CreditNoteServiceImpl creditNoteService;

    private Tenant tenant;
    private Product product;
    private Customer customer;
    private Invoice invoice;
    private InvoiceItem invoiceItem;

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

        invoiceItem = InvoiceItem.builder()
                .id(10L).product(product).productNameSnapshot(product.getProductName())
                .quantity(new BigDecimal("5")).unit("PCS").unitPricePaise(15000L)
                .gstRatePercent(new BigDecimal("18.00"))
                .lineSubtotalPaise(75000L).lineGstPaise(13500L).lineTotalPaise(88500L).build();
        invoice = Invoice.builder().id(20L).tenant(tenant)
                .invoiceNumber("INV-000020").customer(customer)
                .subtotalPaise(75000L).gstAmountPaise(13500L).totalPaise(88500L)
                .status(InvoiceStatus.UNPAID)
                .items(new ArrayList<>(List.of(invoiceItem))).build();

        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(invoiceRepository.findByIdAndTenantId(20L, 1L)).thenReturn(Optional.of(invoice));
        when(documentSequenceService.next(DocumentType.CREDIT_NOTE, 1L)).thenReturn("CN-000001");
        when(creditNoteItemRepository.sumCreditedQuantity(10L)).thenReturn(BigDecimal.ZERO);
        when(creditNoteRepository.save(any(CreditNote.class))).thenAnswer(i -> {
            CreditNote c = i.getArgument(0);
            if (c.getId() == null) c.setId(9L);
            // Real Hibernate/IDENTITY cascade-assigns child ids synchronously
            // on save, which CreditNoteMapper.toResponse() relies on
            // (Comparator.comparing(CreditNoteItem::getId) NPEs on a null
            // key once there is more than one item to actually compare) -
            // this mock must mirror that or a multi-line request fails only
            // in the test double, never in real usage.
            long nextItemId = 1L;
            for (var item : c.getItems()) {
                if (item.getId() == null) item.setId(nextItemId++);
            }
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

    private CreditNoteRequest request(BigDecimal quantity) {
        return new CreditNoteRequest(20L, List.of(new CreditNoteItemRequest(10L, quantity)),
                "Damaged in transit", null);
    }

    @Test
    @DisplayName("issuing a credit note prices the return at the invoice line's effective rate and restores stock")
    void createPricesReturnAtEffectiveRateAndRestoresStock() {
        // Line was 5 x 150.00 = 750.00 net, 18% GST = 135.00 -> effective rate 150.00/unit.
        // Returning 2 units: 300.00 subtotal, 54.00 GST, 354.00 total.
        CreditNoteResponse response = creditNoteService.create(request(new BigDecimal("2")), null);

        assertThat(response.subtotalDisplay()).isEqualTo("300.00");
        assertThat(response.gstAmountDisplay()).isEqualTo("54.00");
        assertThat(response.totalDisplay()).isEqualTo("354.00");
        assertThat(response.status()).isEqualTo(CreditNoteStatus.ISSUED);
        verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("2")), eq(MovementType.SALES_RETURN),
                eq("CREDIT_NOTE"), eq(9L), any());
    }

    @Test
    @DisplayName("returning more than the invoice line's remaining quantity is rejected")
    void createRejectsOverReturn() {
        assertThatThrownBy(() -> creditNoteService.create(request(new BigDecimal("6")), null))
                .isInstanceOf(BusinessException.class);
        verify(stockService, never()).applyMovement(anyLong(), any(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("a second credit note is capped by what an earlier one already returned")
    void createRejectsOverReturnAcrossMultipleCreditNotes() {
        when(creditNoteItemRepository.sumCreditedQuantity(10L)).thenReturn(new BigDecimal("4"));

        assertThatThrownBy(() -> creditNoteService.create(request(new BigDecimal("2")), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("two lines in the SAME request against the same invoice line cannot jointly over-credit it")
    void createRejectsOverReturnAcrossTwoLinesInTheSameRequest() {
        // The invoice line has 5 units total, none credited yet in the
        // database (sumCreditedQuantity stubbed to 0 in setUp). A single
        // request asking for 3 + 3 = 6 must be rejected even though each
        // individual line, checked against the database alone, looks fine -
        // this is exactly the bug: sumCreditedQuantity only sees committed
        // credit notes, not a sibling line still being built in this same
        // request.
        CreditNoteRequest request = new CreditNoteRequest(20L,
                List.of(new CreditNoteItemRequest(10L, new BigDecimal("3")),
                        new CreditNoteItemRequest(10L, new BigDecimal("3"))),
                "Testing joint over-return within one request", null);

        assertThatThrownBy(() -> creditNoteService.create(request, null))
                .isInstanceOf(BusinessException.class);
        verify(creditNoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("two lines in the same request against the same invoice line are both accepted when they fit")
    void createAcceptsTwoLinesInTheSameRequestWhenTheyFit() {
        // 2 + 3 = 5, exactly the invoice line's full quantity - must succeed,
        // and the combined credited quantity must be correct.
        CreditNoteRequest request = new CreditNoteRequest(20L,
                List.of(new CreditNoteItemRequest(10L, new BigDecimal("2")),
                        new CreditNoteItemRequest(10L, new BigDecimal("3"))),
                "Split return, same line", null);

        CreditNoteResponse response = creditNoteService.create(request, null);

        assertThat(response.subtotalDisplay()).isEqualTo("750.00");
        assertThat(response.items()).hasSize(2);
    }

    @Test
    @DisplayName("returning against a cancelled invoice is rejected")
    void createRejectsCancelledInvoice() {
        invoice.setStatus(InvoiceStatus.CANCELLED);

        assertThatThrownBy(() -> creditNoteService.create(request(new BigDecimal("1")), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("an invoice line id that does not belong to the given invoice is rejected")
    void createRejectsInvoiceItemNotOnInvoice() {
        CreditNoteRequest request = new CreditNoteRequest(20L,
                List.of(new CreditNoteItemRequest(999L, new BigDecimal("1"))), "Wrong line", null);

        assertThatThrownBy(() -> creditNoteService.create(request, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("cancelling an issued credit note reverses the stock it restored")
    void cancelReversesStock() {
        var item = com.hardware.erp.creditnote.entity.CreditNoteItem.builder()
                .id(1L).invoiceItem(invoiceItem).product(product).productNameSnapshot(product.getProductName())
                .quantity(new BigDecimal("2")).unit("PCS").unitPricePaise(15000L)
                .gstRatePercent(new BigDecimal("18.00"))
                .lineSubtotalPaise(30000L).lineGstPaise(5400L).lineTotalPaise(35400L).build();
        CreditNote creditNote = CreditNote.builder().id(1L).tenant(tenant)
                .creditNoteNumber("CN-000001").invoice(invoice).customer(customer)
                .reason("Damaged").subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(CreditNoteStatus.ISSUED)
                .items(new ArrayList<>(List.of(item))).build();
        when(creditNoteRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(creditNote));

        CreditNoteResponse response = creditNoteService.cancel(1L);

        assertThat(response.status()).isEqualTo(CreditNoteStatus.CANCELLED);
        verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("-2")), eq(MovementType.SALES_RETURN_REVERSAL),
                eq("CREDIT_NOTE"), eq(1L), any());
    }

    @Test
    @DisplayName("a cancelled credit note cannot be cancelled again")
    void cancelRejectsAlreadyCancelled() {
        CreditNote creditNote = CreditNote.builder().id(1L).tenant(tenant)
                .creditNoteNumber("CN-000001").invoice(invoice).customer(customer)
                .reason("Damaged").subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(CreditNoteStatus.CANCELLED).build();
        when(creditNoteRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(creditNote));

        assertThatThrownBy(() -> creditNoteService.cancel(1L))
                .isInstanceOf(BusinessException.class);
    }
}
