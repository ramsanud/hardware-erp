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
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.entity.TenantExportFormat;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantDataExportServiceImplTest {

    private static final Long ADMIN_ID = 42L;

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
    @Mock private TenantExportLogWriter exportLogWriter;
    @Mock private PlatformAuditService auditService;
    @Mock private PlatformAdminRepository platformAdminRepository;

    private TenantDataExportServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new TenantDataExportServiceImpl(
                tenantRepository, productRepository, customerRepository, supplierRepository,
                invoiceRepository, quotationRepository, purchaseRepository, businessExpenseRepository,
                workerRepository, exportRepository, exportLogWriter, auditService, platformAdminRepository,
                objectMapper);

        when(tenantRepository.existsById(1L)).thenReturn(true);
        when(tenantRepository.getReferenceById(1L)).thenReturn(
                com.hardware.erp.tenant.entity.Tenant.builder().id(1L).slug("t").name("T").build());
        when(platformAdminRepository.getReferenceById(ADMIN_ID))
                .thenReturn(PlatformAdmin.builder().id(ADMIN_ID).email("ops@platform.test").build());

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
        byte[] body = service.export(1L, TenantExportFormat.JSON, ADMIN_ID, null);

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.has("products")).isTrue();
        assertThat(root.has("customers")).isTrue();
        assertThat(root.path("products")).hasSize(1);
        assertThat(root.path("products").get(0).path("productName").asText()).isEqualTo("Hammer");
        assertThat(root.path("customers").get(0).path("customerName").asText()).isEqualTo("Ramesh");

        // 1 product + 1 customer, everything else empty.
        verify(exportLogWriter).completed(eq(1L), eq(ADMIN_ID), eq(TenantExportFormat.JSON), eq(2), eq((long) body.length));
        verify(exportLogWriter, never()).failed(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("export() as CSV produces a real zip with one entry per module")
    void csvExportProducesZipWithEntries() throws Exception {
        byte[] body = service.export(1L, TenantExportFormat.CSV, ADMIN_ID, null);

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

        assertThatThrownBy(() -> service.export(999L, TenantExportFormat.JSON, ADMIN_ID, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("TENANT_NOT_FOUND"));

        verify(exportLogWriter, never()).completed(anyLong(), anyLong(), any(), anyInt(), anyLong());
    }

    // ------------------------------------------------------------------
    // CR-059 - audit of the export lifecycle. Downloading an entire tenant's
    // dataset previously left no row in platform_audit_log at all.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a successful export records REQUESTED then COMPLETED against the tenant, naming the acting admin")
    void successfulExportRecordsRequestedThenCompleted() {
        byte[] body = service.export(1L, TenantExportFormat.JSON, ADMIN_ID, null);

        ArgumentCaptor<PlatformAdmin> admin = ArgumentCaptor.forClass(PlatformAdmin.class);
        verify(auditService).record(eq(PlatformAuditAction.TENANT_EXPORT_REQUESTED), admin.capture(), eq(true),
                eq("TENANT"), eq(1L), eq("JSON"), isNull());
        assertThat(admin.getValue().getId()).isEqualTo(ADMIN_ID);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(eq(PlatformAuditAction.TENANT_EXPORT_COMPLETED), any(), eq(true),
                eq("TENANT"), eq(1L), detail.capture(), isNull());
        assertThat(detail.getValue()).isEqualTo("JSON, 2 records, %d bytes".formatted(body.length));
    }

    @Test
    @DisplayName("the audit detail carries volume metadata only - never a name or a field from the exported file")
    void auditDetailNeverCarriesExportedContent() {
        service.export(1L, TenantExportFormat.JSON, ADMIN_ID, null);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(eq(PlatformAuditAction.TENANT_EXPORT_COMPLETED), any(), eq(true),
                eq("TENANT"), eq(1L), detail.capture(), isNull());
        assertThat(detail.getValue()).doesNotContain("Ramesh", "9999999999", "Hammer", "P1", "C1");
    }

    @Test
    @DisplayName("an export against a tenant that does not exist is still audited - REQUESTED, then FAILED with success=false")
    void unknownTenantIsStillAudited() {
        when(tenantRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.export(999L, TenantExportFormat.CSV, ADMIN_ID, null))
                .isInstanceOf(BusinessException.class);

        verify(auditService).record(eq(PlatformAuditAction.TENANT_EXPORT_REQUESTED), any(), eq(true),
                eq("TENANT"), eq(999L), eq("CSV"), isNull());
        verify(auditService).record(eq(PlatformAuditAction.TENANT_EXPORT_FAILED), any(), eq(false),
                eq("TENANT"), eq(999L), eq("Tenant not found."), isNull());
    }

    @Test
    @DisplayName("a failed export is logged and audited, and the audit detail names the exception type - never its message, which can quote business data")
    void failedExportIsLoggedAndAuditedWithoutLeakingTheMessage() {
        // A driver/serialization failure that quotes the offending value is
        // exactly the case that must not reach the audit trail.
        when(productRepository.findByTenantId(1L))
                .thenThrow(new IllegalStateException("could not serialize customer Ramesh (9999999999)"));

        assertThatThrownBy(() -> service.export(1L, TenantExportFormat.JSON, ADMIN_ID, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("EXPORT_FAILED"));

        // The operational log row keeps the full message - it is a diagnostic, not evidence.
        verify(exportLogWriter).failed(1L, ADMIN_ID, TenantExportFormat.JSON,
                "could not serialize customer Ramesh (9999999999)");

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(eq(PlatformAuditAction.TENANT_EXPORT_FAILED), any(), eq(false),
                eq("TENANT"), eq(1L), detail.capture(), isNull());
        assertThat(detail.getValue()).isEqualTo("JSON export failed: IllegalStateException");
        assertThat(detail.getValue()).doesNotContain("Ramesh", "9999999999");
    }
}
