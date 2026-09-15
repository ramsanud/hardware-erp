package com.hardware.erp.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-086 / CR-087. One registered customer (checksum-valid GSTIN, intra-state)
 * buys one seeded product with a part payment, then every report and every
 * export is read back. The figures asserted are the ones that invoice must
 * produce: 2 x Rs 550 at 18% = Rs 1,100 taxable, Rs 198 GST, Rs 1,298 total,
 * Rs 300 received, Rs 998 due.
 */
class ReportControllerIT extends AbstractIntegrationTest {

    private static final String SHOP_GSTIN = "33AABCS1429B1Z1";
    private static final String CUSTOMER_GSTIN = "33AACCK7821M1ZD";

    @Autowired private ProductRepository productRepository;
    @Autowired private TenantRepository tenantRepository;

    private String owner;
    private String invoiceNumber;

    @BeforeEach
    void seedOneSale() throws Exception {
        owner = bearer(OWNER_MOBILE, OWNER_PASSWORD);

        Tenant tenant = tenantRepository.findById(1L).orElseThrow();
        tenant.setGstNo(SHOP_GSTIN);
        tenant.setStateCode("33");
        tenantRepository.save(tenant);

        Long productId = productRepository.findByTenantIdAndProductCodeIgnoreCase(1L, "PRD-000010")
                .orElseThrow().getId();
        String body = json(Map.of(
                "customerName", "Report Customer",
                "customerMobile", "9898989898",
                "customerGstNo", CUSTOMER_GSTIN,
                "customerStateCode", "33",
                "items", List.of(Map.of("productId", productId, "quantity", 2)),
                "initialPaymentPaise", 30_000,
                "paymentMethod", "CASH"));
        MvcResult created = mockMvc.perform(post("/v1/invoices").header("Authorization", owner)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        invoiceNumber = tree(created.getResponse().getContentAsString()).path("data").path("invoiceNumber").asText();
    }

    private JsonNode data(String path) throws Exception {
        String body = mockMvc.perform(get(path).header("Authorization", owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data");
    }

    private String range() {
        LocalDate today = LocalDate.now();
        return "from=" + today.minusDays(1) + "&to=" + today.plusDays(1);
    }

    @Test
    @DisplayName("day book lists the sale and its receipt with the right kinds and totals")
    void dayBook() throws Exception {
        JsonNode report = data("/v1/reports/day-book?" + range());
        List<String> kinds = report.path("entries").findValuesAsText("kind");
        assertThat(kinds).contains("SALE", "RECEIPT");
        JsonNode sale = null;
        for (JsonNode e : report.path("entries")) {
            if (e.path("kind").asText().equals("SALE") && e.path("reference").asText().equals(invoiceNumber)) sale = e;
        }
        assertThat(sale).isNotNull();
        assertThat(sale.path("amountPaise").asLong()).isEqualTo(129_800);
        assertThat(sale.path("amountDisplay").asText()).isEqualTo("1,298.00");
        assertThat(sale.path("party").asText()).isEqualTo("Report Customer");
        assertThat(report.path("totals").path("receiptsPaise").asLong()).isGreaterThanOrEqualTo(30_000);
        assertThat(report.path("totals").path("netCashPaise").asLong())
                .isEqualTo(report.path("totals").path("receiptsPaise").asLong()
                        - report.path("totals").path("expensesPaise").asLong());
    }

    @Test
    @DisplayName("receivables ageing puts the Rs 998 balance in the 0-30 bucket for that customer")
    void receivablesAgeing() throws Exception {
        JsonNode report = data("/v1/reports/receivables-ageing");
        JsonNode row = null;
        for (JsonNode r : report.path("rows")) {
            if (r.path("customerName").asText().equals("Report Customer")) row = r;
        }
        assertThat(row).isNotNull();
        assertThat(row.path("current0To30Paise").asLong()).isGreaterThanOrEqualTo(99_800);
        assertThat(row.path("over90Paise").asLong()).isZero();
        assertThat(row.path("openInvoices").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(report.path("totals").path("totalPaise").asLong())
                .isEqualTo(report.path("totals").path("current0To30Paise").asLong()
                        + report.path("totals").path("days31To60Paise").asLong()
                        + report.path("totals").path("days61To90Paise").asLong()
                        + report.path("totals").path("over90Paise").asLong());
    }

    @Test
    @DisplayName("stock valuation values quantity on hand at the purchase price and sums it")
    void stockValuation() throws Exception {
        JsonNode report = data("/v1/reports/stock-valuation");
        assertThat(report.path("rows").size()).isGreaterThan(0);
        long sum = 0;
        for (JsonNode r : report.path("rows")) {
            sum += r.path("costValuePaise").asLong();
            assertThat(r.path("productName").asText()).isNotBlank();
        }
        assertThat(report.path("totals").path("costValuePaise").asLong()).isEqualTo(sum);
        assertThat(report.path("totals").path("products").asInt()).isEqualTo(report.path("rows").size());
    }

    @Test
    @DisplayName("purchase register answers for a range with no bills, and refuses a reversed range")
    void purchaseRegister() throws Exception {
        JsonNode report = data("/v1/reports/purchase-register?" + range());
        assertThat(report.path("totals").path("bills").asInt()).isEqualTo(report.path("rows").size());
        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/v1/reports/purchase-register?from=" + today + "&to=" + today.minusDays(1))
                        .header("Authorization", owner))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GST summary shows the 18% slab split evenly into CGST and SGST for an intra-state sale")
    void gstSummary() throws Exception {
        JsonNode report = data("/v1/reports/gst-summary?" + range());
        JsonNode eighteen = null;
        for (JsonNode r : report.path("outward").path("rows")) {
            if (r.path("ratePercent").asDouble() == 18.0) eighteen = r;
        }
        assertThat(eighteen).isNotNull();
        assertThat(eighteen.path("taxablePaise").asLong()).isGreaterThanOrEqualTo(110_000);
        assertThat(eighteen.path("cgstPaise").asLong()).isEqualTo(eighteen.path("sgstPaise").asLong());
        assertThat(eighteen.path("igstPaise").asLong()).isZero();
        assertThat(report.path("netTaxPaise").asLong())
                .isEqualTo(report.path("netCgstPaise").asLong() + report.path("netSgstPaise").asLong()
                        + report.path("netIgstPaise").asLong());
    }

    @Test
    @DisplayName("every report downloads as a PDF and as a workbook; any other format is a 4xx")
    void exports() throws Exception {
        for (String path : List.of("day-book?" + range(), "receivables-ageing?", "stock-valuation?",
                "purchase-register?" + range(), "gst-summary?" + range())) {
            byte[] pdf = mockMvc.perform(get("/v1/reports/" + path.replace("?", "/export?") + "&format=pdf")
                            .header("Authorization", owner))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(pdf).as(path + " pdf").startsWith("%PDF-".getBytes());

            MvcResult xlsx = mockMvc.perform(get("/v1/reports/" + path.replace("?", "/export?") + "&format=xlsx")
                            .header("Authorization", owner))
                    .andExpect(status().isOk())
                    .andReturn();
            assertThat(xlsx.getResponse().getContentType()).contains("spreadsheetml");
            assertThat(xlsx.getResponse().getHeader("Content-Disposition")).contains(".xlsx");
            // An OOXML package is a zip: PK header.
            assertThat(xlsx.getResponse().getContentAsByteArray()).startsWith("PK".getBytes());
        }
        mockMvc.perform(get("/v1/reports/stock-valuation/export?format=csv").header("Authorization", owner))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("STAFF holds no REPORT_VIEW and gets 403; MANAGER holds it and gets 200")
    void permissionGate() throws Exception {
        mockMvc.perform(get("/v1/reports/stock-valuation").header("Authorization", bearer(STAFF_MOBILE, STAFF_PASSWORD)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/reports/stock-valuation").header("Authorization", bearer(MANAGER_MOBILE, MANAGER_PASSWORD)))
                .andExpect(status().isOk());
        // GSTR-1 is REPORT_FINANCIAL: the manager may read reports but not file returns.
        mockMvc.perform(get("/v1/reports/gstr1?period=" + period()).header("Authorization", bearer(MANAGER_MOBILE, MANAGER_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    private static String period() {
        LocalDate today = LocalDate.now();
        return String.format("%02d%d", today.getMonthValue(), today.getYear());
    }

    @Test
    @DisplayName("GSTR-1 files the registered buyer under b2b with a rate-wise item and an HSN line")
    void gstr1() throws Exception {
        JsonNode r = data("/v1/reports/gstr1?period=" + period());
        assertThat(r.path("gstin").asText()).isEqualTo(SHOP_GSTIN);
        assertThat(r.path("fp").asText()).isEqualTo(period());

        JsonNode buyer = null;
        for (JsonNode b : r.path("b2b")) {
            if (b.path("ctin").asText().equals(CUSTOMER_GSTIN)) buyer = b;
        }
        assertThat(buyer).as("b2b entry for the registered customer").isNotNull();
        JsonNode inv = null;
        for (JsonNode i : buyer.path("inv")) {
            if (i.path("inum").asText().equals(invoiceNumber)) inv = i;
        }
        assertThat(inv).isNotNull();
        assertThat(inv.path("pos").asText()).isEqualTo("33");
        assertThat(inv.path("val").asDouble()).isEqualTo(1298.00);
        JsonNode det = inv.path("itms").get(0).path("itm_det");
        assertThat(det.path("rt").asDouble()).isEqualTo(18.0);
        assertThat(det.path("txval").asDouble()).isEqualTo(1100.00);
        assertThat(det.path("camt").asDouble()).isEqualTo(99.00);
        assertThat(det.path("samt").asDouble()).isEqualTo(99.00);
        assertThat(det.has("iamt")).isFalse();

        boolean hsnSeen = false;
        for (JsonNode h : r.path("hsn").path("data")) {
            if (h.path("hsn_sc").asText().equals("8301")) {
                hsnSeen = true;
                assertThat(h.path("uqc").asText()).isEqualTo("NOS-NUMBERS");
            }
        }
        assertThat(hsnSeen).as("HSN 8301 summarised").isTrue();

        // The bare download is the same document without the envelope.
        MvcResult file = mockMvc.perform(get("/v1/reports/gstr1/download?period=" + period()).header("Authorization", owner))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(file.getResponse().getHeader("Content-Disposition")).contains("GSTR1-" + period() + ".json");
        assertThat(tree(file.getResponse().getContentAsString()).path("gstin").asText()).isEqualTo(SHOP_GSTIN);

        mockMvc.perform(get("/v1/reports/gstr1?period=132026").header("Authorization", owner))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("a GSTIN with a wrong check character is refused on the invoice, the same as on the customer")
    void checksumOnDocuments() throws Exception {
        Long productId = productRepository.findByTenantIdAndProductCodeIgnoreCase(1L, "PRD-000010").orElseThrow().getId();
        String body = json(Map.of(
                "customerName", "Typo Customer", "customerMobile", "9898989897",
                "customerGstNo", "33AACCK7821M1ZR", "customerStateCode", "33",
                "items", List.of(Map.of("productId", productId, "quantity", 1))));
        mockMvc.perform(post("/v1/invoices").header("Authorization", owner).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/v1/customers").header("Authorization", owner).contentType(APPLICATION_JSON)
                        .content(json(Map.of("customerName", "Typo", "mobileNo", "9898989896", "gstNo", "33AACCK7821M1ZR"))))
                .andExpect(status().is4xxClientError());
    }
}
