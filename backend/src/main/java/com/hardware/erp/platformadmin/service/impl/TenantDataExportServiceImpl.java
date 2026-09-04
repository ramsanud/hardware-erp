package com.hardware.erp.platformadmin.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.repository.CustomerRepository;
import com.hardware.erp.expense.entity.BusinessExpense;
import com.hardware.erp.expense.repository.BusinessExpenseRepository;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.labour.entity.Worker;
import com.hardware.erp.labour.repository.WorkerRepository;
import com.hardware.erp.platformadmin.dto.TenantExportLogResponse;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.entity.TenantExportFormat;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.platformadmin.repository.PlatformTenantExportRepository;
import com.hardware.erp.platformadmin.service.PlatformAuditService;
import com.hardware.erp.platformadmin.service.TenantDataExportService;
import com.hardware.erp.platformadmin.service.TenantExportLogWriter;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.purchase.entity.Purchase;
import com.hardware.erp.purchase.repository.PurchaseRepository;
import com.hardware.erp.quotation.entity.Quotation;
import com.hardware.erp.quotation.repository.QuotationRepository;
import com.hardware.erp.supplier.entity.Supplier;
import com.hardware.erp.supplier.repository.SupplierRepository;
import com.hardware.erp.tenant.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * CR-057 phase 11 - Backup Center's real, buildable half: an on-demand
 * export of a tenant's own core business records. See V50's migration
 * comment and TenantDataExportService's own javadoc for why this is
 * deliberately not presented as an automated backup - this app has no
 * snapshot/blob infrastructure to back that claim.
 *
 * Deliberately flat, scalar fields only - no nested line-item collections
 * (invoice/purchase/quotation items). A full relational dump is a
 * materially bigger feature (referential structure, versioning) that
 * would need its own scoping, not a "while I'm in here" addition; this is
 * the honestly-bounded, still genuinely useful version.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantDataExportServiceImpl implements TenantDataExportService {

    /** Same target_type string PlatformAdminTenantService already writes for suspend/reactivate, so the Audit Log viewer's target filter groups them together. */
    private static final String TARGET_TYPE = "TENANT";

    private final TenantRepository tenantRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final InvoiceRepository invoiceRepository;
    private final QuotationRepository quotationRepository;
    private final PurchaseRepository purchaseRepository;
    private final BusinessExpenseRepository businessExpenseRepository;
    private final WorkerRepository workerRepository;
    private final PlatformTenantExportRepository exportRepository;
    private final TenantExportLogWriter exportLogWriter;
    private final PlatformAuditService auditService;
    private final PlatformAdminRepository platformAdminRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public byte[] export(Long tenantId, TenantExportFormat format, Long adminId, HttpServletRequest request) {
        // A lazy proxy, never loaded - PlatformAuditService only reads getId().
        // Same pattern as PlatformAdminTenantService.suspend().
        PlatformAdmin actingAdmin = adminId == null ? null : platformAdminRepository.getReferenceById(adminId);

        // Recorded before the tenant is resolved, so an export probe against a
        // tenant id that does not exist is evidenced too - and so that no path
        // out of this method can leave an attempt unrecorded.
        auditService.record(PlatformAuditAction.TENANT_EXPORT_REQUESTED, actingAdmin, true,
                TARGET_TYPE, tenantId, format.name(), request);

        if (!tenantRepository.existsById(tenantId)) {
            auditService.record(PlatformAuditAction.TENANT_EXPORT_FAILED, actingAdmin, false,
                    TARGET_TYPE, tenantId, "Tenant not found.", request);
            throw new BusinessException("Tenant not found.", HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND");
        }

        try {
            Map<String, List<Map<String, Object>>> data = collectData(tenantId);
            byte[] body = format == TenantExportFormat.JSON ? toJson(data) : toCsvZip(data);
            int recordCount = data.values().stream().mapToInt(List::size).sum();

            exportLogWriter.completed(tenantId, adminId, format, recordCount, body.length);
            // Volume metadata only - never a field, a row, or a customer name
            // from the file itself. The audit log records that an export left
            // the system, not what was in it.
            auditService.record(PlatformAuditAction.TENANT_EXPORT_COMPLETED, actingAdmin, true,
                    TARGET_TYPE, tenantId,
                    "%s, %d records, %d bytes".formatted(format.name(), recordCount, body.length), request);
            return body;
        } catch (Exception e) {
            log.error("Tenant data export failed for tenant {}", tenantId, e);
            exportLogWriter.failed(tenantId, adminId, format,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            // The exception type, not its message: a serialization failure can
            // quote the offending value, which would put exported business data
            // into the audit trail. The full message stays on the export log row
            // (platform_tenant_export.error_detail), which is operational, not
            // an evidence record.
            auditService.record(PlatformAuditAction.TENANT_EXPORT_FAILED, actingAdmin, false,
                    TARGET_TYPE, tenantId,
                    "%s export failed: %s".formatted(format.name(), e.getClass().getSimpleName()), request);
            throw new BusinessException(
                    "Could not build the export. This has been logged.", HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_FAILED");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantExportLogResponse> history(Long tenantId) {
        return exportRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(e -> new TenantExportLogResponse(
                        e.getId(), e.getFormat(), e.getStatus(), e.getRecordCount(), e.getFileSizeBytes(),
                        e.getErrorDetail(), e.getCreatedAt()))
                .toList();
    }

    private Map<String, List<Map<String, Object>>> collectData(Long tenantId) {
        Map<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
        data.put("products", productRepository.findByTenantId(tenantId).stream().map(this::row).toList());
        data.put("customers", customerRepository.findByTenantId(tenantId).stream().map(this::row).toList());
        data.put("suppliers", supplierRepository.findByTenantId(tenantId).stream().map(this::row).toList());
        data.put("invoices", invoiceRepository.findByTenantId(tenantId).stream().map(this::row).toList());
        data.put("quotations", quotationRepository.findByTenantId(tenantId).stream().map(this::row).toList());
        data.put("purchases", purchaseRepository.findByTenantId(tenantId).stream().map(this::row).toList());
        data.put("expenses", businessExpenseRepository.findByTenantId(tenantId).stream().map(this::row).toList());
        data.put("workers", workerRepository.findByTenantId(tenantId).stream().map(this::row).toList());
        return data;
    }

    private Map<String, Object> row(Product p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("productCode", p.getProductCode());
        m.put("productName", p.getProductName());
        m.put("category", p.getCategory() == null ? null : p.getCategory().getCategoryName());
        m.put("brand", p.getBrand() == null ? null : p.getBrand().getBrandName());
        m.put("unit", p.getUnit());
        m.put("hsnCode", p.getHsnCode());
        m.put("gstRatePercent", p.getGstRatePercent());
        m.put("purchasePricePaise", p.getPurchasePricePaise());
        m.put("sellingPricePaise", p.getSellingPricePaise());
        m.put("mrpPaise", p.getMrpPaise());
        m.put("status", p.getStatus());
        return m;
    }

    private Map<String, Object> row(Customer c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("customerCode", c.getCustomerCode());
        m.put("customerName", c.getCustomerName());
        m.put("mobileNo", c.getMobileNo());
        m.put("email", c.getEmail());
        m.put("gstNo", c.getGstNo());
        m.put("city", c.getCity());
        m.put("creditLimitPaise", c.getCreditLimitPaise());
        m.put("status", c.getStatus());
        return m;
    }

    private Map<String, Object> row(Supplier s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("supplierCode", s.getSupplierCode());
        m.put("supplierName", s.getSupplierName());
        m.put("mobileNo", s.getMobileNo());
        m.put("email", s.getEmail());
        m.put("gstNo", s.getGstNo());
        m.put("city", s.getCity());
        m.put("creditLimitPaise", s.getCreditLimitPaise());
        m.put("status", s.getStatus());
        return m;
    }

    private Map<String, Object> row(Invoice i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("invoiceNumber", i.getInvoiceNumber());
        m.put("invoiceDate", i.getInvoiceDate());
        m.put("customer", i.getCustomer() == null ? null : i.getCustomer().getCustomerName());
        m.put("totalPaise", i.getTotalPaise());
        m.put("paidPaise", i.getPaidPaise());
        m.put("balancePaise", i.getBalancePaise());
        m.put("status", i.getStatus());
        return m;
    }

    private Map<String, Object> row(Quotation q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.getId());
        m.put("quotationNumber", q.getQuotationNumber());
        m.put("quotationDate", q.getQuotationDate());
        m.put("customer", q.getCustomer() == null ? null : q.getCustomer().getCustomerName());
        m.put("totalPaise", q.getTotalPaise());
        m.put("status", q.getStatus());
        return m;
    }

    private Map<String, Object> row(Purchase p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("purchaseNumber", p.getPurchaseNumber());
        m.put("purchaseDate", p.getPurchaseDate());
        m.put("supplier", p.getSupplier() == null ? null : p.getSupplier().getSupplierName());
        m.put("totalPaise", p.getTotalPaise());
        m.put("paidPaise", p.getPaidPaise());
        m.put("balancePaise", p.getBalancePaise());
        m.put("status", p.getStatus());
        return m;
    }

    private Map<String, Object> row(BusinessExpense e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("expenseDate", e.getExpenseDate());
        m.put("category", e.getCategory() == null ? null : e.getCategory().getName());
        m.put("amountPaise", e.getAmountPaise());
        m.put("paymentMethod", e.getPaymentMethod());
        m.put("status", e.getStatus());
        return m;
    }

    private Map<String, Object> row(Worker w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId());
        m.put("name", w.getName());
        m.put("mobileNo", w.getMobileNo());
        m.put("roleTitle", w.getRoleTitle());
        m.put("dailyRatePaise", w.getDailyRatePaise());
        m.put("status", w.getStatus());
        return m;
    }

    private byte[] toJson(Map<String, List<Map<String, Object>>> data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to serialize tenant export as JSON", new java.io.IOException(e));
        }
    }

    private byte[] toCsvZip(Map<String, List<Map<String, Object>>> data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, List<Map<String, Object>>> entry : data.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey() + ".csv"));
                writeCsv(zip, entry.getValue());
                zip.closeEntry();
            }
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to build tenant export CSV zip", new java.io.IOException(e));
        }
        return out.toByteArray();
    }

    private void writeCsv(java.io.OutputStream out, List<Map<String, Object>> rows) throws Exception {
        // Zip entries must not be closed by the writer - CSVPrinter closes what it wraps, so wrap without auto-closing.
        OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8) {
            @Override public void close() { /* the ZipOutputStream entry lifecycle owns closing, not this writer */ }
        };
        if (rows.isEmpty()) {
            writer.flush();
            return;
        }
        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                .setHeader(rows.get(0).keySet().toArray(new String[0])).build())) {
            for (Map<String, Object> row : rows) {
                printer.printRecord(row.values());
            }
        }
    }
}
