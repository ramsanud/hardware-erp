package com.hardware.erp.invoice.pdf;

import com.hardware.erp.common.image.QrCodeGenerator;
import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.common.util.IndianStates;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.entity.InvoiceItem;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantBankAccount;
import com.hardware.erp.tenant.entity.TenantBankAccountQr;
import com.hardware.erp.tenant.entity.TenantLogo;
import com.hardware.erp.tenant.entity.TenantSignature;
import com.hardware.erp.tenant.entity.TenantUpiQr;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

/**
 * Builds a multi-page GST tax invoice as HTML and renders it to PDF
 * server-side (CR-022, extended for the full document layout). No stored
 * binary - regenerated on every request from the invoice's own data, same
 * principle as CR-018's "decrypt on demand".
 *
 * CGST+SGST vs IGST is decided here, not stored: intra-state (customer
 * state code blank or equal to the shop's) splits each line's GST evenly
 * into CGST+SGST; anything else is IGST in full. Pagination for a long
 * item list is natural CSS table flow (a repeating &lt;thead&gt; across
 * pages), not a hardcoded row-per-page count - that adapts correctly
 * regardless of font size or paper settings, which a fixed "15 rows"
 * threshold would not.
 */
@Service
public class InvoicePdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    public byte[] render(Invoice invoice, Tenant tenant) {
        return render(invoice, tenant, null, null, null);
    }

    public byte[] render(Invoice invoice, Tenant tenant, TenantSignature signature) {
        return render(invoice, tenant, signature, null, null);
    }

    public byte[] render(Invoice invoice, Tenant tenant, TenantSignature signature, TenantLogo logo) {
        return render(invoice, tenant, signature, logo, null);
    }

    public byte[] render(Invoice invoice, Tenant tenant, TenantSignature signature, TenantLogo logo, TenantUpiQr upiQr) {
        String html = buildHtml(invoice, tenant, signature, logo, upiQr);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to render invoice PDF",
                    new java.io.IOException(e));
        }
        return out.toByteArray();
    }

    private String buildHtml(Invoice invoice, Tenant tenant, TenantSignature signature, TenantLogo logo, TenantUpiQr upiQr) {
        Customer customer = invoice.getCustomer();
        boolean interState = isInterState(tenant, customer);

        StringBuilder rows = new StringBuilder();
        int serial = 1;
        for (InvoiceItem item : invoice.getItems()) {
            String hsn = item.getProduct() != null && item.getProduct().getHsnCode() != null
                    ? item.getProduct().getHsnCode() : "-";
            long lineGst = item.getLineGstPaise();
            long lineCgst = interState ? 0 : lineGst / 2;
            long lineSgst = interState ? 0 : lineGst - lineCgst;
            long lineIgst = interState ? lineGst : 0;

            rows.append("<tr>")
                    .append(textCell(String.valueOf(serial++)))
                    .append(textCell(escape(item.getProductNameSnapshot())))
                    .append(textCell(escape(hsn)))
                    .append(textCell(escape(item.getUnit())))
                    .append(cell(item.getQuantity().stripTrailingZeros().toPlainString()))
                    .append(cell(IndianCurrencyFormat.rupees(item.getUnitPricePaise())))
                    .append(cell(IndianCurrencyFormat.rupees(item.getLineSubtotalPaise())))
                    .append(cell(lineCgst > 0 ? IndianCurrencyFormat.rupees(lineCgst) : "-"))
                    .append(cell(lineSgst > 0 ? IndianCurrencyFormat.rupees(lineSgst) : "-"))
                    .append(cell(lineIgst > 0 ? IndianCurrencyFormat.rupees(lineIgst) : "-"))
                    .append(cell(IndianCurrencyFormat.rupees(item.getLineTotalPaise())))
                    .append("</tr>");
        }

        long gstTotal = invoice.getGstAmountPaise();
        long cgstTotal = interState ? 0 : gstTotal / 2;
        long sgstTotal = interState ? 0 : gstTotal - cgstTotal;
        long igstTotal = interState ? gstTotal : 0;

        String taxRows = interState
                ? row("IGST", IndianCurrencyFormat.rupees(igstTotal))
                : row("CGST", IndianCurrencyFormat.rupees(cgstTotal)) + row("SGST", IndianCurrencyFormat.rupees(sgstTotal));

        boolean hasShipment = anyNonBlank(invoice.getTransportMode(), invoice.getVehicleNumber(), invoice.getDeliveryAddress());

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE html>\n"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
            + "<head><style>\n"
            + stylesheet()
            + "</style></head>\n"
            + "<body>\n"
            + "  <div class=\"title\">TAX INVOICE</div>\n"
            + companyBlock(tenant, logo)
            + "  <table class=\"header\"><tr>\n"
            + "    <td class=\"box\">\n"
            + "      <div class=\"muted\">Billed To</div>\n"
            + "      <div><b>" + escape(customer.getCustomerName()) + "</b></div>\n"
            + "      <div>" + addressLines(customer) + "</div>\n"
            + "      <div>Mobile: " + escape(customer.getMobileNo()) + "</div>\n"
            + "      <div>GSTIN: " + escape(orNotSet(customer.getGstNo())) + "</div>\n"
            + "    </td>\n"
            + "    <td style=\"width:4%\"></td>\n"
            + "    <td class=\"box\">\n"
            + "      <div><b>Invoice No:</b> " + escape(invoice.getInvoiceNumber()) + "</div>\n"
            + "      <div><b>Invoice Date:</b> " + invoice.getInvoiceDate().format(DATE_FORMAT) + "</div>\n"
            + "      <div><b>Place of Supply:</b> " + escape(orNotSet(IndianStates.nameOrCode(customer.getStateCode()))) + "</div>\n"
            + "      <div><b>Country of Supply:</b> India</div>\n"
            + "    </td>\n"
            + "  </tr></table>\n"
            + (hasShipment ? shipmentBlock(invoice) : "")
            + "  <table class=\"items\">\n"
            + "    <thead><tr>\n"
            + "      <th>#</th><th>Item Description</th><th>HSN/SAC</th><th>UQC</th><th class=\"num\">Qty</th>\n"
            + "      <th class=\"num\">Rate</th><th class=\"num\">Taxable Value</th>\n"
            + "      <th class=\"num\">CGST</th><th class=\"num\">SGST</th><th class=\"num\">IGST</th><th class=\"num\">Total</th>\n"
            + "    </tr></thead>\n"
            + "    <tbody>" + rows + "</tbody>\n"
            + "  </table>\n"
            + "  <table class=\"totals\">\n"
            + (invoice.getDiscountPaise() != null && invoice.getDiscountPaise() > 0
                ? row("Subtotal (before discount)",
                        IndianCurrencyFormat.rupees(invoice.getSubtotalPaise() + invoice.getDiscountPaise()))
                    + row("Discount" + (invoice.getCoupon() != null ? " (" + escape(invoice.getCoupon().getCode()) + ")" : ""),
                        "-" + IndianCurrencyFormat.rupees(invoice.getDiscountPaise()))
                : "")
            + row("Subtotal", IndianCurrencyFormat.rupees(invoice.getSubtotalPaise()))
            + taxRows
            + "    <tr class=\"grand\"><td class=\"label\">Total</td>"
            + "<td class=\"value\">Rs. " + IndianCurrencyFormat.rupees(invoice.getTotalPaise()) + "</td></tr>\n"
            + "  </table>\n"
            + "  <div class=\"clear\"></div>\n"
            + "  <div class=\"section-divider\"></div>\n"
            + paymentBlock(tenant, invoice, upiQr)
            + "  <div class=\"terms\">\n"
            + "    <div class=\"muted\" style=\"font-weight:bold; margin-bottom:3px;\">Terms and Conditions</div>\n"
            + "    <ol>\n"
            + "      <li>Customer will pay GST and delivery charges.</li>\n"
            + "      <li>No return after 15 days.</li>\n"
            + "    </ol>\n"
            + "    <p>Certified that the particulars given above are true and correct.</p>\n"
            + "  </div>\n"
            + "  <div class=\"signature\">\n"
            + "    <div>For " + escape(tenant.getName()) + "</div>\n"
            + signatureBlock(signature)
            + "    <div class=\"clear\"></div>\n"
            + "    <div>" + escape(orNotSet(tenant.getSignatoryName())) + "</div>\n"
            + "    <div class=\"muted\">Authorized Signatory</div>\n"
            + "  </div>\n"
            + "  <div class=\"clear\"></div>\n"
            + "  <div class=\"thank-you\">Thank you for shopping with us!</div>\n"
            + "  <div class=\"footer-note\">This is a computer-generated invoice and does not require a physical signature.</div>\n"
            + "</body></html>";
    }

    private static String stylesheet() {
        return "  @page { size: A4; margin: 24px 24px 36px 24px;\n"
            + "    @bottom-center { content: \"Page \" counter(page) \" of \" counter(pages); font-size: 8px; color: #888; } }\n"
            + "  body { font-family: Helvetica, Arial, sans-serif; font-size: 10px; color: #1a1a1a; }\n"
            + "  h1 { font-size: 16px; margin: 0 0 2px 0; }\n"
            + "  .muted { color: #555; }\n"
            + "  .title { text-align: center; font-size: 15px; font-weight: bold; letter-spacing: 1.5px;\n"
            + "           color: #1e3a5f; margin-bottom: 8px; }\n"
            + "  .company { width: 100%; border-top: 3px solid #1e3a5f; margin-bottom: 10px; box-sizing: border-box; }\n"
            + "  .company .logo { float: right; max-height: 48px; max-width: 140px; margin-left: 12px; }\n"
            + "  .header { width: 100%; margin-top: 8px; }\n"
            + "  .header td { vertical-align: top; padding: 0; }\n"
            + "  .box { border: 1px solid #ccc; background: #f7f8fa; padding: 6px; width: 48%; }\n"
            + "  table.items { width: 100%; border-collapse: collapse; margin-top: 10px;\n"
            + "    -fs-table-paginate: paginate; }\n"
            + "  table.items thead { display: table-header-group; }\n"
            + "  table.items tr { page-break-inside: avoid; }\n"
            + "  table.items th, table.items td { border: 1px solid #ccc; padding: 4px 5px; }\n"
            + "  table.items th { background: #1e3a5f; color: #fff; text-align: left; font-weight: normal; }\n"
            + "  table.items tbody tr:nth-child(even) { background: #f7f8fa; }\n"
            + "  .num { text-align: right; }\n"
            + "  table.totals { width: 40%; margin-left: 60%; border-collapse: collapse; margin-top: 6px; }\n"
            + "  table.totals td { padding: 3px 5px; }\n"
            + "  table.totals td.label { text-align: right; color: #555; }\n"
            + "  table.totals td.value { text-align: right; width: 90px; }\n"
            + "  table.totals tr.grand td { border-top: 1px solid #333; font-weight: bold; font-size: 11px; }\n"
            + "  .section-divider { margin-top: 14px; border-top: 1px dashed #b8c4d0; }\n"
            + "  .payment { width: 100%; margin-top: 10px; background: #f4f8fb; padding: 8px; box-sizing: border-box; }\n"
            + "  .payment td { vertical-align: top; padding: 0; }\n"
            + "  .qr-box { text-align: center; width: 130px; }\n"
            + "  .qr-box img { width: 100px; height: 100px; }\n"
            + "  .terms { margin-top: 14px; font-size: 9px; }\n"
            + "  .terms ol { margin: 2px 0 6px 16px; padding: 0; }\n"
            + "  .signature { margin-top: 20px; text-align: right; }\n"
            + "  .signature .line { margin-top: 34px; border-top: 1px solid #333; width: 180px; float: right; }\n"
            + "  .signature-img { max-height: 60px; max-width: 180px; float: right; margin-top: 6px; }\n"
            + "  .clear { clear: both; }\n"
            + "  .thank-you { margin-top: 16px; text-align: center; font-size: 11px; font-weight: bold;\n"
            + "               color: #1e3a5f; }\n"
            + "  .footer-note { margin-top: 4px; text-align: center; font-size: 8.5px; color: #777; }\n";
    }

    /**
     * Boxed to match Billed To/Shipment (CR-025) - previously the shop's
     * own details sat unboxed above everything else, which read as
     * visually inconsistent next to the customer's boxed section right
     * below it. The logo (uploaded in Shop Settings, CR-023) was captured
     * months ago but never actually threaded through to this PDF until now.
     */
    private static String companyBlock(Tenant tenant, TenantLogo logo) {
        StringBuilder sb = new StringBuilder("  <div class=\"company box\">\n");
        if (logo != null) {
            String base64 = Base64.getEncoder().encodeToString(logo.getImageData());
            sb.append("    <img class=\"logo\" src=\"data:").append(logo.getContentType())
                    .append(";base64,").append(base64).append("\" alt=\"").append(escape(tenant.getName())).append(" logo\" />\n");
        }
        sb.append("    <h1>").append(escape(tenant.getName())).append("</h1>\n");
        sb.append("    <div class=\"muted\">").append(addressLines(tenant)).append("</div>\n");
        sb.append("    <div><b>GSTIN:</b> ").append(escape(orNotSet(tenant.getGstNo())))
                .append("&#160;&#160;&#160;<b>PAN:</b> ").append(escape(orNotSet(tenant.getPanNo()))).append("</div>\n");
        String contact = joinNonBlank(", ", tenant.getPhone(), tenant.getEmail());
        if (!contact.isBlank()) {
            sb.append("    <div class=\"muted\">").append(escape(contact)).append("</div>\n");
        }
        sb.append("    <div class=\"clear\"></div>\n");
        sb.append("  </div>\n");
        return sb.toString();
    }

    private static String shipmentBlock(Invoice invoice) {
        return "  <table class=\"header\"><tr><td class=\"box\" style=\"width:100%\">\n"
            + "    <div class=\"muted\" style=\"font-weight:bold;\">Shipment Details</div>\n"
            + "    <div><b>Transport Mode:</b> " + escape(orNotSet(invoice.getTransportMode())) + "</div>\n"
            + "    <div><b>Vehicle Number:</b> " + escape(orNotSet(invoice.getVehicleNumber())) + "</div>\n"
            + "    <div><b>Delivery Address:</b> " + escape(orNotSet(invoice.getDeliveryAddress())) + "</div>\n"
            + "  </td></tr></table>\n";
    }

    private static String paymentBlock(Tenant tenant, Invoice invoice, TenantUpiQr upiQr) {
        // CR-036: an invoice with an explicitly selected bank account always
        // wins over the shop's single default fields - this is the whole
        // point of "owner picks which account+QR to show" per invoice. Every
        // pre-CR-036 invoice (and any tenant that never adds a second
        // account) has bankAccount == null and falls straight through to the
        // original single-account behaviour below, untouched.
        if (invoice.getBankAccount() != null) {
            return paymentBlockForAccount(invoice.getBankAccount(), invoice.getBankAccountQr(), invoice);
        }
        boolean hasBank = anyNonBlank(tenant.getBankAccountNo(), tenant.getBankIfsc());
        boolean hasUpi = tenant.getUpiId() != null && !tenant.getUpiId().isBlank();
        if (!hasBank && !hasUpi) {
            return "";
        }

        StringBuilder sb = new StringBuilder("  <table class=\"payment\"><tr>\n");
        sb.append("    <td>\n");
        if (hasBank) {
            sb.append("      <div class=\"muted\" style=\"font-weight:bold;\">Bank &amp; Payment Details</div>\n");
            sb.append("      <div><b>Account Name:</b> ").append(escape(orNotSet(tenant.getBankAccountName()))).append("</div>\n");
            sb.append("      <div><b>Account Number:</b> ").append(escape(orNotSet(tenant.getBankAccountNo()))).append("</div>\n");
            sb.append("      <div><b>IFSC:</b> ").append(escape(orNotSet(tenant.getBankIfsc()))).append("</div>\n");
            sb.append("      <div><b>Bank:</b> ").append(escape(orNotSet(tenant.getBankName()))).append("</div>\n");
            if (hasUpi) {
                sb.append("      <div><b>UPI ID:</b> ").append(escape(tenant.getUpiId())).append("</div>\n");
            }
        } else {
            sb.append("      <div><b>UPI ID:</b> ").append(escape(tenant.getUpiId())).append("</div>\n");
        }
        sb.append("    </td>\n");
        if (upiQr != null) {
            // A real GPay/PhonePe QR the shop already has, uploaded as-is - takes
            // priority over the generated one since it's the code customers may
            // already have scanned before and trust.
            String base64 = Base64.getEncoder().encodeToString(upiQr.getImageData());
            sb.append("    <td class=\"qr-box\">\n");
            sb.append("      <img src=\"data:").append(upiQr.getContentType())
                    .append(";base64,").append(base64).append("\" alt=\"UPI QR code\" />\n");
            sb.append("      <div class=\"muted\">Scan to Pay</div>\n");
            sb.append("    </td>\n");
        } else if (hasUpi) {
            BigDecimal amountRupees = BigDecimal.valueOf(invoice.getBalancePaise())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            byte[] qrPng = QrCodeGenerator.upiPaymentQrPng(
                    tenant.getUpiId(), tenant.getName(), invoice.getInvoiceNumber(), amountRupees);
            String base64 = Base64.getEncoder().encodeToString(qrPng);
            sb.append("    <td class=\"qr-box\">\n");
            sb.append("      <img src=\"data:image/png;base64,").append(base64).append("\" alt=\"UPI QR code\" />\n");
            sb.append("      <div class=\"muted\">Scan to Pay</div>\n");
            sb.append("    </td>\n");
        }
        sb.append("  </tr></table>\n");
        return sb.toString();
    }

    /**
     * CR-036 - an invoice with an explicitly selected bank account. selectedQr
     * is the one the owner actually chose at invoice-creation time; if none
     * was chosen but the account has QR codes uploaded, the first one is
     * shown rather than leaving the payment section QR-less for no reason.
     * If the account has no QR at all, an account-holder's own UPI id still
     * gets a generated QR (same fallback the single-account path already
     * has) - only when there is truly nothing to show does this fall back to
     * "account details only", per spec.
     */
    private static String paymentBlockForAccount(TenantBankAccount account, TenantBankAccountQr selectedQr, Invoice invoice) {
        StringBuilder sb = new StringBuilder("  <table class=\"payment\"><tr>\n");
        sb.append("    <td>\n");
        sb.append("      <div class=\"muted\" style=\"font-weight:bold;\">Bank &amp; Payment Details</div>\n");
        sb.append("      <div><b>Account Name:</b> ").append(escape(orNotSet(account.getAccountHolderName()))).append("</div>\n");
        sb.append("      <div><b>Account Number:</b> ").append(escape(orNotSet(account.getAccountNumber()))).append("</div>\n");
        sb.append("      <div><b>IFSC:</b> ").append(escape(orNotSet(account.getIfscCode()))).append("</div>\n");
        sb.append("      <div><b>Bank:</b> ").append(escape(orNotSet(account.getBankName()))).append("</div>\n");
        boolean hasUpi = account.getUpiId() != null && !account.getUpiId().isBlank();
        if (hasUpi) {
            sb.append("      <div><b>UPI ID:</b> ").append(escape(account.getUpiId())).append("</div>\n");
        }
        sb.append("    </td>\n");

        TenantBankAccountQr qrToShow = selectedQr != null ? selectedQr
                : account.getQrCodes().isEmpty() ? null : account.getQrCodes().get(0);

        if (qrToShow != null) {
            String base64 = Base64.getEncoder().encodeToString(qrToShow.getImageData());
            sb.append("    <td class=\"qr-box\">\n");
            sb.append("      <img src=\"data:").append(qrToShow.getContentType())
                    .append(";base64,").append(base64).append("\" alt=\"").append(escape(qrToShow.getLabel())).append("\" />\n");
            sb.append("      <div class=\"muted\">").append(escape(qrToShow.getLabel())).append(" - Scan to Pay</div>\n");
            sb.append("    </td>\n");
        } else if (hasUpi) {
            BigDecimal amountRupees = BigDecimal.valueOf(invoice.getBalancePaise())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            byte[] qrPng = QrCodeGenerator.upiPaymentQrPng(
                    account.getUpiId(), account.getAccountHolderName(), invoice.getInvoiceNumber(), amountRupees);
            String base64 = Base64.getEncoder().encodeToString(qrPng);
            sb.append("    <td class=\"qr-box\">\n");
            sb.append("      <img src=\"data:image/png;base64,").append(base64).append("\" alt=\"UPI QR code\" />\n");
            sb.append("      <div class=\"muted\">Scan to Pay</div>\n");
            sb.append("    </td>\n");
        }
        sb.append("  </tr></table>\n");
        return sb.toString();
    }

    /** An actual drawn/uploaded signature (CR-023) takes the place of the blank line when present. */
    private static String signatureBlock(TenantSignature signature) {
        if (signature == null) {
            return "    <div class=\"line\"></div>\n";
        }
        String base64 = Base64.getEncoder().encodeToString(signature.getImageData());
        return "    <img class=\"signature-img\" src=\"data:" + signature.getContentType()
                + ";base64," + base64 + "\" alt=\"Signature\" />\n";
    }

    private static boolean isInterState(Tenant tenant, Customer customer) {
        String shopState = tenant.getStateCode();
        String customerState = customer.getStateCode();
        if (shopState == null || customerState == null) {
            return false;
        }
        return !shopState.equals(customerState);
    }

    private static String addressLines(Tenant tenant) {
        List<String> parts = List.of(
                nullToEmpty(tenant.getAddressLine1()), nullToEmpty(tenant.getAddressLine2()),
                joinCityState(tenant.getCity(), tenant.getPincode()));
        return joinNonBlank(parts);
    }

    private static String addressLines(Customer customer) {
        List<String> parts = List.of(
                nullToEmpty(customer.getAddressLine1()), nullToEmpty(customer.getAddressLine2()),
                joinCityState(customer.getCity(), customer.getPincode()));
        return joinNonBlank(parts);
    }

    private static String joinCityState(String city, String pincode) {
        if (city == null && pincode == null) return "";
        return nullToEmpty(city) + (pincode != null ? " - " + pincode : "");
    }

    private static String joinNonBlank(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) sb.append("<br/>");
                sb.append(escape(part));
            }
        }
        return sb.length() == 0 ? "<span class=\"muted\">Address not set</span>" : sb.toString();
    }

    private static String joinNonBlank(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) sb.append(separator);
                sb.append(part);
            }
        }
        return sb.toString();
    }

    private static boolean anyNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return true;
        }
        return false;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String orNotSet(String value) {
        return (value == null || value.isBlank()) ? "Not set" : value;
    }

    private static String cell(String value) {
        return "<td class=\"num\">" + value + "</td>";
    }

    private static String textCell(String value) {
        return "<td>" + value + "</td>";
    }

    private static String row(String label, String value) {
        return "<tr><td class=\"label\">" + label + "</td><td class=\"value\">Rs. " + value + "</td></tr>\n";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
