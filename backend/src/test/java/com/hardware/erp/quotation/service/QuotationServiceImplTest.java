package com.hardware.erp.quotation.service;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.service.CustomerLookupService;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.service.InvoiceService;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.quotation.dto.QuotationItemRequest;
import com.hardware.erp.quotation.dto.QuotationRequest;
import com.hardware.erp.quotation.dto.QuotationResponse;
import com.hardware.erp.quotation.entity.Quotation;
import com.hardware.erp.quotation.entity.QuotationStatus;
import com.hardware.erp.quotation.mapper.QuotationMapper;
import com.hardware.erp.quotation.repository.QuotationRepository;
import com.hardware.erp.quotation.service.impl.QuotationServiceImpl;
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
class QuotationServiceImplTest {

    @Mock private QuotationRepository quotationRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private CustomerLookupService customerLookupService;
    @Mock private ProductRepository productRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;
    @Mock private InvoiceService invoiceService;

    @Spy private QuotationMapper quotationMapper = new QuotationMapper();

    @InjectMocks private QuotationServiceImpl quotationService;

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
        when(documentSequenceService.next(DocumentType.QUOTATION, 1L)).thenReturn("QUO-000001");
        when(customerLookupService.findOrCreate(anyString(), anyString(), any(), any(), any(), eq(1L)))
                .thenReturn(customer);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(i -> {
            Quotation q = i.getArgument(0);
            if (q.getId() == null) q.setId(9L);
            return q;
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

    private QuotationRequest request(LocalDate validUntil) {
        return new QuotationRequest("Ramesh Traders", "9876500001", null, null, null,
                validUntil, List.of(new QuotationItemRequest(2L, new BigDecimal("2"))), null);
    }

    // 2 x 150.00 = 300.00 subtotal, 18% GST = 54.00, total = 354.00 (35400 paise)

    @Test
    @DisplayName("creating a quotation computes subtotal, GST and total from the current product price")
    void createComputesTotals() {
        QuotationResponse response = quotationService.create(request(LocalDate.now().plusDays(7)));

        assertThat(response.subtotalDisplay()).isEqualTo("300.00");
        assertThat(response.gstAmountDisplay()).isEqualTo("54.00");
        assertThat(response.totalDisplay()).isEqualTo("354.00");
        assertThat(response.status()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(response.expired()).isFalse();
    }

    @Test
    @DisplayName("a quotation past its valid-until date reports itself as expired")
    void expiredQuotationIsFlagged() {
        Quotation quotation = Quotation.builder().id(1L).tenant(tenant)
                .quotationNumber("QUO-000001").customer(customer)
                .quotationDate(LocalDate.now().minusDays(10)).validUntil(LocalDate.now().minusDays(1))
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(QuotationStatus.SENT).build();
        when(quotationRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(quotation));

        QuotationResponse response = quotationService.get(1L);

        assertThat(response.expired()).isTrue();
    }

    @Test
    @DisplayName("converting an expired quotation is rejected, not silently allowed")
    void convertRejectsExpiredQuotation() {
        Quotation quotation = Quotation.builder().id(1L).tenant(tenant)
                .quotationNumber("QUO-000001").customer(customer)
                .quotationDate(LocalDate.now().minusDays(10)).validUntil(LocalDate.now().minusDays(1))
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(QuotationStatus.SENT).build();
        when(quotationRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(quotation));

        assertThatThrownBy(() -> quotationService.convert(1L))
                .isInstanceOf(BusinessException.class);
        verify(invoiceService, never()).create(any());
    }

    @Test
    @DisplayName("a rejected quotation cannot be converted")
    void convertRejectsRejectedStatus() {
        Quotation quotation = Quotation.builder().id(1L).tenant(tenant)
                .quotationNumber("QUO-000001").customer(customer)
                .quotationDate(LocalDate.now()).validUntil(LocalDate.now().plusDays(7))
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(QuotationStatus.REJECTED).build();
        when(quotationRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(quotation));

        assertThatThrownBy(() -> quotationService.convert(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("converting a valid quotation creates an invoice through InvoiceService and marks CONVERTED")
    void convertCreatesInvoiceAndMarksConverted() {
        var item = com.hardware.erp.quotation.entity.QuotationItem.builder()
                .id(1L).product(product).productNameSnapshot(product.getProductName())
                .quantity(new BigDecimal("2")).unitPricePaise(15000L).gstRatePercent(new BigDecimal("18.00"))
                .lineSubtotalPaise(30000L).lineGstPaise(5400L).lineTotalPaise(35400L).build();
        Quotation quotation = Quotation.builder().id(1L).tenant(tenant)
                .quotationNumber("QUO-000001").customer(customer)
                .quotationDate(LocalDate.now()).validUntil(LocalDate.now().plusDays(7))
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(QuotationStatus.SENT)
                .items(new java.util.ArrayList<>(List.of(item))).build();
        when(quotationRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(quotation));
        when(invoiceService.create(any(InvoiceRequest.class))).thenReturn(
                new InvoiceResponse(77L, "INV-000077", 3L, "Ramesh Traders", "9876500001",
                        LocalDate.now(), "300.00", "54.00", "354.00", null, null, null, "0.00", "354.00",
                        com.hardware.erp.invoice.entity.InvoiceStatus.UNPAID, null, null, null, null,
                        List.of(), List.of(), null, null, null, null));

        QuotationResponse response = quotationService.convert(1L);

        assertThat(response.status()).isEqualTo(QuotationStatus.CONVERTED);
        assertThat(response.convertedInvoiceId()).isEqualTo(77L);
        verify(invoiceService).create(any(InvoiceRequest.class));
    }

    @Test
    @DisplayName("a converted quotation's status can never be changed again")
    void statusCannotChangeAfterConversion() {
        Quotation quotation = Quotation.builder().id(1L).tenant(tenant)
                .quotationNumber("QUO-000001").customer(customer)
                .quotationDate(LocalDate.now()).validUntil(LocalDate.now().plusDays(7))
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(QuotationStatus.CONVERTED).convertedInvoiceId(77L).build();
        when(quotationRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(quotation));

        assertThatThrownBy(() -> quotationService.updateStatus(1L, QuotationStatus.REJECTED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("CONVERTED cannot be set directly through the status endpoint")
    void cannotSetConvertedDirectly() {
        Quotation quotation = Quotation.builder().id(1L).tenant(tenant)
                .quotationNumber("QUO-000001").customer(customer)
                .quotationDate(LocalDate.now()).validUntil(LocalDate.now().plusDays(7))
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(QuotationStatus.DRAFT).build();
        when(quotationRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(quotation));

        assertThatThrownBy(() -> quotationService.updateStatus(1L, QuotationStatus.CONVERTED))
                .isInstanceOf(BusinessException.class);
    }
}
