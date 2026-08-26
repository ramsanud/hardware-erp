package com.hardware.erp.purchase.service;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.entity.StockMovement;
import com.hardware.erp.inventory.repository.StockRepository;
import com.hardware.erp.inventory.service.StockService;
import com.hardware.erp.product.dto.ProductRequest;
import com.hardware.erp.product.dto.ProductResponse;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.repository.BrandRepository;
import com.hardware.erp.product.repository.CategoryRepository;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.product.service.ProductService;
import com.hardware.erp.purchase.dto.ImportConfirmRequest;
import com.hardware.erp.purchase.dto.ImportConfirmRow;
import com.hardware.erp.purchase.dto.ImportResultResponse;
import com.hardware.erp.purchase.entity.Purchase;
import com.hardware.erp.purchase.extraction.DocumentExtractionService;
import com.hardware.erp.purchase.mapper.PurchaseMapper;
import com.hardware.erp.purchase.repository.PurchaseDocumentRepository;
import com.hardware.erp.purchase.repository.PurchaseRepository;
import com.hardware.erp.purchase.service.impl.PurchaseImportServiceImpl;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.supplier.entity.Supplier;
import com.hardware.erp.supplier.repository.SupplierRepository;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseImportServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private StockRepository stockRepository;
    @Mock private PurchaseRepository purchaseRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private PurchaseDocumentRepository purchaseDocumentRepository;
    @Mock private ProductService productService;
    @Mock private StockService stockService;
    @Mock private TenantRepository tenantRepository;
    @Mock private PurchaseMapper purchaseMapper;
    @Mock private ActivityLogService activityLog;

    private PurchaseImportServiceImpl importService;

    private Tenant tenant;
    private Supplier supplier;
    private final AtomicLong nextProductId = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        importService = new PurchaseImportServiceImpl(
                List.<DocumentExtractionService>of(), productRepository, brandRepository, categoryRepository,
                supplierRepository, stockRepository, purchaseRepository, documentSequenceService,
                purchaseDocumentRepository,
                productService, stockService, tenantRepository, purchaseMapper, activityLog);

        tenant = Tenant.builder().id(1L).slug("default").name("Default")
                .status(TenantStatus.ACTIVE).build();
        supplier = Supplier.builder().id(3L).tenant(tenant)
                .supplierCode("SUP-0001").supplierName("Godrej Distributors")
                .mobileNo("9823456781").build();

        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(supplierRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(supplier));
        when(documentSequenceService.next(DocumentType.PURCHASE, 1L)).thenReturn("PUR-000001");
        when(purchaseRepository.findPossibleDuplicates(anyLong(), anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(i -> i.getArgument(0));

        // Every call to create a new product returns a fresh id and a matching, findable Product entity -
        // simulates the real service-to-service path createProductForImport() actually uses.
        when(productService.create(any(ProductRequest.class))).thenAnswer(inv -> {
            ProductRequest req = inv.getArgument(0);
            long id = nextProductId.incrementAndGet();
            Product entity = Product.builder().id(id).tenant(tenant)
                    .productCode(req.productCode() != null ? req.productCode() : "PRD-" + id)
                    .productName(req.productName()).unit(req.unit())
                    .gstRatePercent(req.gstRatePercent())
                    .purchasePricePaise(req.purchasePricePaise())
                    .sellingPricePaise(req.sellingPricePaise())
                    .mrpPaise(req.mrpPaise())
                    .status(ProductStatus.ACTIVE).build();
            when(productRepository.findByIdAndTenantId(id, 1L)).thenReturn(Optional.of(entity));
            ProductResponse response = mock(ProductResponse.class);
            when(response.id()).thenReturn(id);
            return response;
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

    @Test
    void twoNewProductRowsSharingTheSameSkuAreMergedIntoOneCreatedProduct() throws Exception {
        // BUG-PUR-003: a supplier bill's own SKU/part number is threaded through as the new
        // product's code. Two rows in the same bill naming the identical new item under two
        // slightly different name spellings, but the same SKU, must not attempt two create()
        // calls for the same code within one transaction - the second would fail outright
        // against data the first just created. SKU is checked before name since it's the
        // stronger identity signal.
        ImportConfirmRow row1 = newProductRow(1, "GI Pipe 1 inch", "GIPIPE-25");
        ImportConfirmRow row2 = newProductRow(2, "G.I. Pipe 1\" ", "GIPIPE-25");
        ImportConfirmRequest request = new ImportConfirmRequest(
                3L, "BILL-2001", LocalDate.now(), List.of(row1, row2), false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "bill.csv", "text/csv", "Product Name,Quantity,Unit,Unit Price\n".getBytes());

        ImportResultResponse result = importService.confirm(request, file);

        verify(productService, times(1)).create(any(ProductRequest.class));
        assertThat(result.newProductsCreated()).isEqualTo(1);
        assertThat(result.rowsMergedWithEarlierRow()).isEqualTo(1);
    }

    @Test
    void newProductTakesTheBillsSkuAsItsProductCode() throws Exception {
        // BUG-PUR-003: previously createProductForImport() always passed null as the product
        // code, silently discarding the bill's own SKU in favour of an auto-generated one.
        ImportConfirmRow row = newProductRow(1, "Brass Tap 1/2 inch", "TAP-BR-05");
        ImportConfirmRequest request = new ImportConfirmRequest(
                3L, "BILL-2002", LocalDate.now(), List.of(row), false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "bill.csv", "text/csv", "Product Name,Quantity,Unit,Unit Price\n".getBytes());

        importService.confirm(request, file);

        org.mockito.ArgumentCaptor<ProductRequest> captor = org.mockito.ArgumentCaptor.forClass(ProductRequest.class);
        verify(productService).create(captor.capture());
        assertThat(captor.getValue().productCode()).isEqualTo("TAP-BR-05");
    }

    private ImportConfirmRow newProductRow(int rowNumber, String productName, String sku) {
        return new ImportConfirmRow(rowNumber, null, productName, sku, null, null, "PCS",
                new BigDecimal("5"), 10000L, new BigDecimal("18"), false);
    }
}
