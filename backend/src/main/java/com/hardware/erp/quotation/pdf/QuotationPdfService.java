package com.hardware.erp.quotation.pdf;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.common.util.IndianStates;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.quotation.entity.Quotation;
import com.hardware.erp.quotation.entity.QuotationItem;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantLogo;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

/**
 * A price quote, not a financial record (CR-022) - no payment/bank/UPI
 * section, since nothing is due yet, and no signature line the way a tax
 * invoice needs one. Deliberately its own service rather than a shared base
 * with InvoicePdfService: the documents diverge enough (title, no payment
 * block, an added "Valid Until" line) that a shared abstraction would mostly
 * be conditionals, not real reuse (CR-026).
 */
@Service
public class QuotationPdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    public byte[] render(Quotation quotation, Tenant tenant) {
        return render(quotation, tenant, null);
    }

    public byte[] render(Quotation quotation, Tenant tenant, TenantLogo logo) {
        String html = buildHtml(quotation, tenant, logo);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to render quotation PDF", new java.io.IOException(e));
        }
        return out.toByteArray();
    }

    private String buildHtml(Quotation quotation, Tenant tenant, TenantLogo logo) {
        Customer customer = quotation.getCustomer();
        boolean interState = isInterState(tenant, customer);

        StringBuilder rows = new StringBuilder();
        int serial = 1;
        for (QuotationItem item : quotation.getItems()) {
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

        long gstTotal = quotation.getGstAmountPaise();
        long cgstTotal = interState ? 0 : gstTotal / 2;
        long sgstTotal = interState ? 0 : gstTotal - cgstTotal;
        long igstTotal = interState ? gstTotal : 0;

        String taxRows = interState
                ? row("IGST", IndianCurrencyFormat.rupees(igstTotal))
                : row("CGST", IndianCurrencyFormat.rupees(cgstTotal)) + row("SGST", IndianCurrencyFormat.rupees(sgstTotal));

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE html>\n"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
            + "<head><style>\n"
            + stylesheet()
            + "</style></head>\n"
            + "<body>\n"
            + "  <div class=\"title\">QUOTATION</div>\n"
            + companyBlock(tenant, logo)
            + "  <table class=\"header\"><tr>\n"
            + "    <td class=\"box\">\n"
            + "      <div class=\"muted\">Quoted To</div>\n"
            + "      <div><b>" + escape(customer.getCustomerName()) + "</b></div>\n"
            + "      <div>" + addressLines(customer) + "</div>\n"
            + "      <div>Mobile: " + escape(customer.getMobileNo()) + "</div>\n"
            + "      <div>GSTIN: " + escape(orNotSet(customer.getGstNo())) + "</div>\n"
            + "    </td>\n"
            + "    <td style=\"width:4%\"></td>\n"
            + "    <td class=\"box\">\n"
            + "      <div><b>Quotation No:</b> " + escape(quotation.getQuotationNumber()) + "</div>\n"
            + "      <div><b>Date:</b> " + quotation.getQuotationDate().format(DATE_FORMAT) + "</div>\n"
            + "      <div><b>Valid Until:</b> " + quotation.getValidUntil().format(DATE_FORMAT) + "</div>\n"
            + "      <div><b>Place of Supply:</b> " + escape(orNotSet(IndianStates.nameOrCode(customer.getStateCode()))) + "</div>\n"
            + "    </td>\n"
            + "  </tr></table>\n"
            + "  <table class=\"items\">\n"
            + "    <thead><tr>\n"
            + "      <th>#</th><th>Item Description</th><th>HSN/SAC</th><th>UQC</th><th class=\"num\">Qty</th>\n"
            + "      <th class=\"num\">Rate</th><th class=\"num\">Taxable Value</th>\n"
            + "      <th class=\"num\">CGST</th><th class=\"num\">SGST</th><th class=\"num\">IGST</th><th class=\"num\">Total</th>\n"
            + "    </tr></thead>\n"
            + "    <tbody>" + rows + "</tbody>\n"
            + "  </table>\n"
            + "  <table class=\"totals\">\n"
            + row("Subtotal", IndianCurrencyFormat.rupees(quotation.getSubtotalPaise()))
            + taxRows
            + "    <tr class=\"grand\"><td class=\"label\">Total</td>"
            + "<td class=\"value\">Rs. " + IndianCurrencyFormat.rupees(quotation.getTotalPaise()) + "</td></tr>\n"
            + "  </table>\n"
            + "  <div class=\"clear\"></div>\n"
            + "  <div class=\"section-divider\"></div>\n"
            + "  <div class=\"terms\">\n"
            + "    <div class=\"muted\" style=\"font-weight:bold; margin-bottom:3px;\">Terms and Conditions</div>\n"
            + "    <ol>\n"
            + "      <li>Prices quoted are valid only until " + quotation.getValidUntil().format(DATE_FORMAT) + ".</li>\n"
            + "      <li>Final billed rate is the product's price at the time of purchase, not this quote.</li>\n"
            + "    </ol>\n"
            + "  </div>\n"
            + "  <div class=\"thank-you\">Thank you for considering us!</div>\n"
            + "  <div class=\"footer-note\">This is a computer-generated quotation, not a tax invoice.</div>\n"
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
            + "  .terms { margin-top: 14px; font-size: 9px; }\n"
            + "  .terms ol { margin: 2px 0 6px 16px; padding: 0; }\n"
            + "  .clear { clear: both; }\n"
            + "  .thank-you { margin-top: 16px; text-align: center; font-size: 11px; font-weight: bold;\n"
            + "               color: #1e3a5f; }\n"
            + "  .footer-note { margin-top: 4px; text-align: center; font-size: 8.5px; color: #777; }\n";
    }

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
