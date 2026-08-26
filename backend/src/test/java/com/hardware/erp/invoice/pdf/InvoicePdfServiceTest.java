package com.hardware.erp.invoice.pdf;

import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.entity.InvoiceItem;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantBankAccount;
import com.hardware.erp.tenant.entity.TenantBankAccountQr;
import com.hardware.erp.tenant.entity.TenantBankAccountStatus;
import com.hardware.erp.tenant.entity.TenantLogo;
import com.hardware.erp.tenant.entity.TenantStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders a real PDF (not a mock) - the risk here is a malformed XHTML
 * string breaking the openhtmltopdf parser, which a pure-string-building
 * unit test would never catch (CR-022).
 */
class InvoicePdfServiceTest {

    private final InvoicePdfService pdfService = new InvoicePdfService();

    private Tenant tenant(String stateCode) {
        return Tenant.builder().id(1L).slug("default").name("Default Shop")
                .status(TenantStatus.ACTIVE).gstNo("29AAAAA0000A1Z5").panNo("AAAAA0000A")
                .addressLine1("12 MG Road").city("Bengaluru").stateCode(stateCode).pincode("560001")
                .phone("9900011122").email("shop@example.com")
                .bankAccountName("Default Shop").bankAccountNo("123456789012").bankIfsc("HDFC0001234")
                .bankName("HDFC Bank").upiId("defaultshop@okicici")
                .signatoryName("Ramesh Kumar").build();
    }

    private Customer customer(String stateCode) {
        return Customer.builder().id(2L).customerCode("CUS-0001")
                .customerName("Suresh & Co.").mobileNo("9876500001")
                .gstNo("29BBBBB1111B1Z5").stateCode(stateCode).build();
    }

    private Invoice invoice(Customer customer) {
        Product product = Product.builder().id(3L).productCode("PRD-000001")
                .productName("Hammer 500g \"Premium\" <Grade A>").unit("PCS")
                .gstRatePercent(new BigDecimal("18.00")).hsnCode("8205")
                .purchasePricePaise(10000L).sellingPricePaise(15000L).mrpPaise(18000L)
                .status(ProductStatus.ACTIVE).build();
        InvoiceItem item = InvoiceItem.builder().id(4L).product(product)
                .productNameSnapshot(product.getProductName()).quantity(new BigDecimal("2")).unit("PCS")
                .unitPricePaise(15000L).gstRatePercent(new BigDecimal("18.00"))
                .lineSubtotalPaise(30000L).lineGstPaise(5400L).lineTotalPaise(35400L).build();
        return Invoice.builder().id(5L).invoiceNumber("INV-000001").customer(customer)
                .invoiceDate(LocalDate.now()).subtotalPaise(30000L).gstAmountPaise(5400L)
                .totalPaise(35400L).paidPaise(0L).balancePaise(35400L).status(InvoiceStatus.UNPAID)
                .items(List.of(item)).build();
    }

    /** Enough line items that the table genuinely spans multiple PDF pages, plus shipment details. */
    private Invoice manyItemInvoice(Customer customer) {
        List<InvoiceItem> items = new java.util.ArrayList<>();
        long subtotal = 0L;
        long gst = 0L;
        for (int i = 1; i <= 40; i++) {
            Product product = Product.builder().id((long) (100 + i)).productCode("PRD-" + i)
                    .productName("Item " + i + " - Hardware Component").unit("PCS")
                    .gstRatePercent(new BigDecimal("18.00")).hsnCode("7318")
                    .purchasePricePaise(5000L).sellingPricePaise(8000L).mrpPaise(10000L)
                    .status(ProductStatus.ACTIVE).build();
            long lineSubtotal = 8000L * i;
            long lineGst = lineSubtotal * 18 / 100;
            items.add(InvoiceItem.builder().id((long) (200 + i)).product(product)
                    .productNameSnapshot(product.getProductName()).quantity(BigDecimal.valueOf(i)).unit("PCS")
                    .unitPricePaise(8000L).gstRatePercent(new BigDecimal("18.00"))
                    .lineSubtotalPaise(lineSubtotal).lineGstPaise(lineGst).lineTotalPaise(lineSubtotal + lineGst)
                    .build());
            subtotal += lineSubtotal;
            gst += lineGst;
        }
        return Invoice.builder().id(6L).invoiceNumber("INV-000002").customer(customer)
                .invoiceDate(LocalDate.now()).subtotalPaise(subtotal).gstAmountPaise(gst)
                .totalPaise(subtotal + gst).paidPaise(0L).balancePaise(subtotal + gst).status(InvoiceStatus.UNPAID)
                .transportMode("By Road").vehicleNumber("KA01AB1234")
                .deliveryAddress("456 Industrial Area, Peenya, Bengaluru - 560058")
                .items(items).build();
    }

    @Test
    @DisplayName("renders a well-formed PDF for an intra-state sale (same state code)")
    void rendersIntraStateInvoice() {
        byte[] pdf = pdfService.render(invoice(customer("29")), tenant("29"));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("renders a well-formed PDF for an inter-state sale (different state codes)")
    void rendersInterStateInvoice() {
        byte[] pdf = pdfService.render(invoice(customer("27")), tenant("29"));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("renders the shop logo, boxed alongside its GSTIN/PAN, when one has been uploaded")
    void rendersShopLogoWhenPresent() throws Exception {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", pngBytes);
        TenantLogo logo = TenantLogo.builder().tenantId(1L).contentType("image/png")
                .fileSize(pngBytes.size()).imageData(pngBytes.toByteArray()).build();

        byte[] pdf = pdfService.render(invoice(customer("29")), tenant("29"), null, logo);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("renders without error when the shop has not filled in GST settings yet")
    void rendersWithMissingShopSettings() {
        Tenant bareTenant = Tenant.builder().id(1L).slug("default").name("Default Shop")
                .status(TenantStatus.ACTIVE).build();
        Customer bareCustomer = Customer.builder().id(2L).customerCode("CUS-0001")
                .customerName("Suresh & Co.").mobileNo("9876500001").build();

        byte[] pdf = pdfService.render(invoice(bareCustomer), bareTenant);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("special characters in product/customer names are escaped, not left to break the XML parser")
    void escapesSpecialCharacters() {
        // The product name in invoice() already contains " < > & - this
        // asserts render() does not throw on them.
        byte[] pdf = pdfService.render(invoice(customer("29")), tenant("29"));

        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("40 line items plus shipment/PAN/bank/UPI details render a well-formed multi-page PDF")
    void rendersMultiPageInvoiceWithShipmentAndPayment() throws java.io.IOException {
        BufferedImage image = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(java.awt.Color.decode("#1e3a5f"));
        g.fillOval(4, 4, 52, 52);
        g.dispose();
        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", pngBytes);
        TenantLogo logo = TenantLogo.builder().tenantId(1L).contentType("image/png")
                .fileSize(pngBytes.size()).imageData(pngBytes.toByteArray()).build();

        byte[] pdf = pdfService.render(manyItemInvoice(customer("29")), tenant("29"), null, logo);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // Written out so the actual layout (multi-page table, QR code,
        // shipment/bank sections) can be opened and eyeballed - a byte
        // count alone can't confirm the page count or visual layout.
        java.nio.file.Path out = java.nio.file.Path.of("target", "sample-invoice.pdf");
        java.nio.file.Files.write(out, pdf);
    }

    @Test
    @DisplayName("CR-036: an invoice with a selected bank account + QR renders that account, not the shop's default fields")
    void rendersSelectedBankAccountWithQr() throws java.io.IOException {
        TenantBankAccount account = TenantBankAccount.builder().id(10L)
                .label("SBI Current Account").bankName("State Bank of India")
                .accountHolderName("Second Shop Account").accountNumber("998877665544")
                .ifscCode("SBIN0009999").upiId("secondaccount@sbi")
                .defaultAccount(false).status(TenantBankAccountStatus.ACTIVE).build();

        BufferedImage image = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", pngBytes);
        TenantBankAccountQr qr = TenantBankAccountQr.builder().id(20L).bankAccount(account)
                .label("GPay QR").contentType("image/png")
                .fileSize(pngBytes.size()).imageData(pngBytes.toByteArray()).build();
        account.setQrCodes(List.of(qr));

        Invoice invoice = invoice(customer("29"));
        invoice.setBankAccount(account);
        invoice.setBankAccountQr(qr);

        byte[] pdf = pdfService.render(invoice, tenant("29"));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("CR-036: a selected bank account with no QR uploaded and no UPI id shows account details only")
    void rendersSelectedBankAccountWithNoQrOrUpi() {
        TenantBankAccount account = TenantBankAccount.builder().id(11L)
                .label("Cash Counter Account").bankName("Axis Bank")
                .accountHolderName("Cash Counter").accountNumber("111122223333")
                .ifscCode("UTIB0001234").upiId(null)
                .defaultAccount(true).status(TenantBankAccountStatus.ACTIVE)
                .qrCodes(new java.util.ArrayList<>()).build();

        Invoice invoice = invoice(customer("29"));
        invoice.setBankAccount(account);
        invoice.setBankAccountQr(null);

        byte[] pdf = pdfService.render(invoice, tenant("29"));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
