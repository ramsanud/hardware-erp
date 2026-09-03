package com.hardware.erp.platformadmin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.entity.CustomerStatus;
import com.hardware.erp.customer.repository.CustomerRepository;
import com.hardware.erp.expense.repository.BusinessExpenseRepository;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.labour.repository.WorkerRepository;
import com.hardware.erp.platformadmin.entity.PlatformTenantExport;
import com.hardware.erp.platformadmin.entity.TenantExportFormat;
import com.hardware.erp.platformadmin.entity.TenantExportStatus;
import com.hardware.erp.platformadmin.repository.PlatformTenantExportRepository;
import com.hardware.erp.platformadmin.service.impl.TenantDataExportServiceImpl;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.purchase.repository.PurchaseRepository;
import com.hardware.erp.quotation.repository.QuotationRepository;
import com.hardware.erp.supplier.repository.SupplierRepository;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantDataExportServiceImplTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private QuotationRepository quotationRepository;
    @Mock private PurchaseRepository purchaseRepository;
    @Mock private BusinessExpenseRepository businessExpenseRepository;
    @Mock private WorkerRepository workerRepository;
    @Mock private PlatformTenantExportRepository exportRepository;

    private TenantDataExportServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new TenantDataExportServiceImpl(
                tenantRepository, productRepository, customerRepository, supplierRepository,
                invoiceRepository, quotationRepository, purchaseRepository, businessExpenseRepository,
                workerRepository, exportRepository, objectMapper);

        when(tenantRepository.existsById(1L)).thenReturn(true);
        when(tenantRepository.getReferenceById(1L)).thenReturn(
                com.hardware.erp.tenant.entity.Tenant.builder().id(1L).slug("t").name("T").build());
        when(exportRepository.save(any(PlatformTenantExport.class))).thenAnswer(i -> i.getArgument(0));

        when(productRepository.findByTenantId(1L)).thenReturn(List.of(
                Product.builder().id(1L).productCode("P1").productName("Hammer").unit("pc")
                        .status(ProductStatus.ACTIVE).build()));
        when(customerRepository.findByTenantId(1L)).thenReturn(List.of(
                Customer.builder().id(1L).customerCode("C1").customerName("Ramesh").mobileNo("9999999999")
                        .status(CustomerStatus.ACTIVE).build()));
        when(supplierRepository.findByTenantId(1L)).thenReturn(List.of());
        when(invoiceRepository.findByTenantId(1L)).thenReturn(List.of());
        when(quotationRepository.findByTenantId(1L)).thenReturn(List.of());
        when(purchaseRepository.findByTenantId(1L)).thenReturn(List.of());
        when(businessExpenseRepository.findByTenantId(1L)).thenReturn(List.of());
        when(workerRepository.findByTenantId(1L)).thenReturn(List.of());
    }

    @Test
    @DisplayName("export() as JSON produces a valid document with one key per module and logs a COMPLETED entry")
    void jsonExportProducesValidDocument() throws Exception {
        byte[] body = service.export(1L, TenantExportFormat.JSON, 42L);

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.has("products")).isTrue();
        assertThat(root.has("customers")).isTrue();
        assertThat(root.path("products")).hasSize(1);
        assertThat(root.path("products").get(0).path("productName").asText()).isEqualTo("Hammer");
        assertThat(root.path("customers").get(0).path("customerName").asText()).isEqualTo("Ramesh");

        ArgumentCaptor<PlatformTenantExport> captor = ArgumentCaptor.forClass(PlatformTenantExport.class);
        verify(exportRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TenantExportStatus.COMPLETED);
        assertThat(captor.getValue().getRecordCount()).isEqualTo(2); // 1 product + 1 customer, everything else empty
        assertThat(captor.getValue().getAdminId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("export() as CSV produces a real zip with one entry per module")
    void csvExportProducesZipWithEntries() throws Exception {
        byte[] body = service.export(1L, TenantExportFormat.CSV, 42L);

        List<String> entries = new java.util.ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        assertThat(entries).contains("products.csv", "customers.csv", "suppliers.csv", "invoices.csv",
                "quotations.csv", "purchases.csv", "expenses.csv", "workers.csv");
    }

    @Test
    @DisplayName("an unknown tenant is refused with 404, never a partial export")
    void unknownTenantRefused() {
        when(tenantRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.export(999L, TenantExportFormat.JSON, 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("TENANT_NOT_FOUND"));
    }
}
