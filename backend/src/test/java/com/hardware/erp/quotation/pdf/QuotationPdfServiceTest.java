package com.hardware.erp.quotation.pdf;

import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.quotation.entity.Quotation;
import com.hardware.erp.quotation.entity.QuotationItem;
import com.hardware.erp.quotation.entity.QuotationStatus;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantLogo;
import com.hardware.erp.tenant.entity.TenantStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders a real PDF, same rationale as InvoicePdfServiceTest - a malformed XHTML string is caught here, not in production. */
class QuotationPdfServiceTest {

    private final QuotationPdfService pdfService = new QuotationPdfService();

    private Tenant tenant(String stateCode) {
        return Tenant.builder().id(1L).slug("default").name("Default Shop")
                .status(TenantStatus.ACTIVE).gstNo("29AAAAA0000A1Z5").panNo("AAAAA0000A")
                .addressLine1("12 MG Road").city("Bengaluru").stateCode(stateCode).pincode("560001")
                .phone("9900011122").email("shop@example.com").build();
    }

    private Customer customer(String stateCode) {
        return Customer.builder().id(2L).customerCode("CUS-0001")
                .customerName("Suresh & Co.").mobileNo("9876500001")
                .gstNo("29BBBBB1111B1Z5").stateCode(stateCode).build();
    }

    private Quotation quotation(Customer customer) {
        Product product = Product.builder().id(3L).productCode("PRD-000001")
                .productName("Hammer 500g \"Premium\" <Grade A>").unit("PCS")
                .gstRatePercent(new BigDecimal("18.00")).hsnCode("8205")
                .purchasePricePaise(10000L).sellingPricePaise(15000L).mrpPaise(18000L)
                .status(ProductStatus.ACTIVE).build();
        QuotationItem item = QuotationItem.builder().id(4L).product(product)
                .productNameSnapshot(product.getProductName()).quantity(new BigDecimal("2")).unit("PCS")
                .unitPricePaise(15000L).gstRatePercent(new BigDecimal("18.00"))
                .lineSubtotalPaise(30000L).lineGstPaise(5400L).lineTotalPaise(35400L).build();
        return Quotation.builder().id(5L).quotationNumber("QUO-000001").customer(customer)
                .quotationDate(LocalDate.now()).validUntil(LocalDate.now().plusDays(15))
                .subtotalPaise(30000L).gstAmountPaise(5400L).totalPaise(35400L)
                .status(QuotationStatus.DRAFT).items(List.of(item)).build();
    }

    @Test
    @DisplayName("renders a well-formed PDF for an intra-state quote (same state code)")
    void rendersIntraStateQuotation() {
        byte[] pdf = pdfService.render(quotation(customer("29")), tenant("29"));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("renders a well-formed PDF for an inter-state quote (different state codes)")
    void rendersInterStateQuotation() {
        byte[] pdf = pdfService.render(quotation(customer("27")), tenant("29"));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("renders the shop logo when present, and writes a sample PDF for visual inspection")
    void rendersLogoAndWritesSample() throws Exception {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", pngBytes);
        TenantLogo logo = TenantLogo.builder().tenantId(1L).contentType("image/png")
                .fileSize(pngBytes.size()).imageData(pngBytes.toByteArray()).build();

        byte[] pdf = pdfService.render(quotation(customer("29")), tenant("29"), logo);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        Files.write(Path.of("target", "sample-quotation.pdf"), pdf);
    }

    @Test
    @DisplayName("renders without error when the shop has not filled in GST settings yet")
    void rendersWithMissingShopSettings() {
        Tenant bareTenant = Tenant.builder().id(1L).slug("default").name("Default Shop")
                .status(TenantStatus.ACTIVE).build();
        Customer bareCustomer = Customer.builder().id(2L).customerCode("CUS-0001")
                .customerName("Suresh & Co.").mobileNo("9876500001").build();

        byte[] pdf = pdfService.render(quotation(bareCustomer), bareTenant);

        assertThat(pdf).isNotEmpty();
    }
}
