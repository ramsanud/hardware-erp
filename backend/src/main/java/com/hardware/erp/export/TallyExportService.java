package com.hardware.erp.export;

import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.util.IndianStates;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.entity.InvoiceItem;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.purchase.entity.Purchase;
import com.hardware.erp.purchase.entity.PurchaseItem;
import com.hardware.erp.purchase.repository.PurchaseRepository;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.supplier.entity.Supplier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * CR-053 backlog item 2. Orchestrates TallyXmlBuilder against real
 * tenant-scoped data for one date range - see that class's own header
 * comment for the scope and the sign-convention caveat.
 */
@Service
@RequiredArgsConstructor
public class TallyExportService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InvoiceRepository invoiceRepository;
    private final PurchaseRepository purchaseRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public byte[] exportXml(LocalDate fromDate, LocalDate toDate) {
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException("The start date must be on or before the end date");
        }
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Tenant tenant = tenantRepository.getReferenceById(tenantId);

        var invoices = invoiceRepository.findForExport(tenantId, fromDate, toDate);
        var purchases = purchaseRepository.findForExport(tenantId, fromDate, toDate);

        StringBuilder xml = new StringBuilder();
        xml.append(TallyXmlBuilder.envelopeStart(tenant.getName()));
        xml.append(TallyXmlBuilder.messageStart());

        // Masters first - Tally resolves a voucher's ledger/stock-item
        // references by name, so they must already exist (or arrive earlier
        // in the same import) before any voucher naming them.
        if (!invoices.isEmpty() || !purchases.isEmpty()) {
            xml.append(TallyXmlBuilder.systemLedger("Sales Account", "Sales Accounts"));
            xml.append(TallyXmlBuilder.systemLedger("Purchase Account", "Purchase Accounts"));
            xml.append(TallyXmlBuilder.systemLedger("CGST", "Duties & Taxes"));
            xml.append(TallyXmlBuilder.systemLedger("SGST", "Duties & Taxes"));
            xml.append(TallyXmlBuilder.systemLedger("IGST", "Duties & Taxes"));
        }

        Set<Long> seenCustomers = new LinkedHashSet<>();
        Set<Long> seenSuppliers = new LinkedHashSet<>();
        Set<Long> seenProducts = new LinkedHashSet<>();

        for (Invoice invoice : invoices) {
            Customer customer = invoice.getCustomer();
            if (seenCustomers.add(customer.getId())) {
                xml.append(TallyXmlBuilder.partyLedger(partyName(customer.getCustomerName(), customer.getId()),
                        "Sundry Debtors", customer.getGstNo(), IndianStates.nameOrCode(customer.getStateCode())));
            }
            for (InvoiceItem item : invoice.getItems()) {
                Product product = item.getProduct();
                if (product != null && seenProducts.add(product.getId())) {
                    xml.append(TallyXmlBuilder.stockItemMaster(product.getProductName(), product.getUnit(),
                            product.getHsnCode(), product.getGstRatePercent()));
                }
            }
        }
        for (Purchase purchase : purchases) {
            Supplier supplier = purchase.getSupplier();
            if (seenSuppliers.add(supplier.getId())) {
                xml.append(TallyXmlBuilder.partyLedger(partyName(supplier.getSupplierName(), supplier.getId()),
                        "Sundry Creditors", supplier.getGstNo(), IndianStates.nameOrCode(supplier.getStateCode())));
            }
            for (PurchaseItem item : purchase.getItems()) {
                Product product = item.getProduct();
                if (product != null && seenProducts.add(product.getId())) {
                    xml.append(TallyXmlBuilder.stockItemMaster(product.getProductName(), product.getUnit(),
                            product.getHsnCode(), product.getGstRatePercent()));
                }
            }
        }

        // Vouchers. GST is split CGST+SGST (intra-state) or IGST
        // (inter-state) at the whole-voucher level - a coarser breakdown
        // than InvoicePdfService's per-rate table, a deliberate
        // simplification within this feature's stated "ledger-level,
        // bounded" scope (see TallyXmlBuilder's header comment).
        for (Invoice invoice : invoices) {
            boolean interState = isInterState(tenant, invoice.getCustomer());
            BigDecimal taxable = rupees(invoice.getSubtotalPaise());
            BigDecimal gst = rupees(invoice.getGstAmountPaise());
            BigDecimal total = rupees(invoice.getTotalPaise());
            BigDecimal cgst = interState ? BigDecimal.ZERO : halveRoundDown(gst);
            BigDecimal sgst = interState ? BigDecimal.ZERO : gst.subtract(cgst);
            BigDecimal igst = interState ? gst : BigDecimal.ZERO;

            xml.append(TallyXmlBuilder.salesVoucher(invoice.getInvoiceDate(), invoice.getInvoiceNumber(),
                    partyName(invoice.getCustomer().getCustomerName(), invoice.getCustomer().getId()),
                    "Sales invoice " + invoice.getInvoiceNumber(), taxable, cgst, sgst, igst, total));
        }
        for (Purchase purchase : purchases) {
            boolean interState = isInterState(tenant, purchase.getSupplier());
            BigDecimal taxable = rupees(purchase.getSubtotalPaise());
            BigDecimal gst = rupees(purchase.getGstAmountPaise());
            BigDecimal total = rupees(purchase.getTotalPaise());
            BigDecimal cgst = interState ? BigDecimal.ZERO : halveRoundDown(gst);
            BigDecimal sgst = interState ? BigDecimal.ZERO : gst.subtract(cgst);
            BigDecimal igst = interState ? gst : BigDecimal.ZERO;

            xml.append(TallyXmlBuilder.purchaseVoucher(purchase.getPurchaseDate(), purchase.getPurchaseNumber(),
                    partyName(purchase.getSupplier().getSupplierName(), purchase.getSupplier().getId()),
                    "Purchase bill " + purchase.getPurchaseNumber(), taxable, cgst, sgst, igst, total));
        }

        xml.append(TallyXmlBuilder.messageEnd());
        xml.append(TallyXmlBuilder.envelopeEnd());
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Two customers (or suppliers) can legitimately share a name - Tally
     * ledgers are keyed on NAME alone, so a bare collision would silently
     * merge two different parties' books together. Appending the id makes
     * every ledger name unique without needing to actually detect the
     * collision case specially.
     */
    private String partyName(String name, Long id) {
        return name.trim() + " (#" + id + ")";
    }

    private static boolean isInterState(Tenant tenant, Customer customer) {
        return differentState(tenant.getStateCode(), customer.getStateCode());
    }

    private static boolean isInterState(Tenant tenant, Supplier supplier) {
        return differentState(tenant.getStateCode(), supplier.getStateCode());
    }

    private static boolean differentState(String shopState, String otherState) {
        if (shopState == null || otherState == null) {
            return false;
        }
        return !shopState.equals(otherState);
    }

    private static BigDecimal halveRoundDown(BigDecimal value) {
        return value.divide(BigDecimal.valueOf(2), 2, RoundingMode.DOWN);
    }

    private static BigDecimal rupees(Long paise) {
        return BigDecimal.valueOf(paise == null ? 0 : paise).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
