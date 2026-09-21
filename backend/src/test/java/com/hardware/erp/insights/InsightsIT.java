package com.hardware.erp.insights;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.invoice.dto.InvoiceItemRequest;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-092 Smart Insights against real rows. Every assertion is about a
 * figure the test itself recorded moments earlier; an empty window must
 * come back empty with a summary that says so - never a placeholder.
 */
class InsightsIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private record Shop(String bearer, Long tenantId) {}

    private Shop registerShop(SubscriptionTier tier) throws Exception {
        String mobile = "9" + (100000000 + ThreadLocalRandom.current().nextInt(899999999));
        String body = mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(json(new TenantRegistrationRequest("Insights Shop " + mobile.substring(5), "Owner",
                                mobile, "owner" + mobile + "@insightstest.example", "Insight@2026",
                                tier, true, "1.0", "1.0", false))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Shop(bearer(mobile, "Insight@2026"), tree(body).path("data").path("tenantId").asLong());
    }

    private Long insertProduct(Long tenantId, String code, String name, String stock, long sellingPaise, long costPaise, String reorderLevel) {
        Long productId = jdbc.queryForObject("""
                INSERT INTO product (tenant_id, product_code, product_name, unit, selling_price_paise,
                                     purchase_price_paise, mrp_paise, gst_rate_percent, reorder_level, status, created_at)
                VALUES (?, ?, ?, 'PCS', ?, ?, ?, 0, CAST(? AS DECIMAL), 'ACTIVE', now())
                RETURNING product_id""", Long.class, tenantId, code, name, sellingPaise, costPaise, sellingPaise, reorderLevel);
        jdbc.update("INSERT INTO stock (tenant_id, product_id, quantity_on_hand, average_cost_paise, created_at) VALUES (?, ?, CAST(? AS DECIMAL), ?, now())",
                tenantId, productId, stock, costPaise);
        return productId;
    }

    private void sell(Shop shop, String mobile, List<InvoiceItemRequest> items) throws Exception {
        mockMvc.perform(post("/v1/invoices").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new InvoiceRequest("Insight Customer", mobile, null, null, null, items, null, null, null))))
                .andExpect(status().isCreated());
    }

    private JsonNode insight(Shop shop, String path) throws Exception {
        String body = mockMvc.perform(get("/v1/insights/" + path).header("Authorization", shop.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data");
    }

    @Test
    @DisplayName("slow-moving, reorder, bought-together and pricing each report exactly what was recorded, and nothing for an empty window")
    void insightsReflectRecordedRows() throws Exception {
        Shop shop = registerShop(SubscriptionTier.MAX);
        // Sells well, nearly out: 2 left, reorder level 5.
        Long hammer = insertProduct(shop.tenantId(), "IN-1", "Insight Hammer", "12", 50000, 30000, "5");
        // Never sells, worth a lot at cost.
        Long anvil = insertProduct(shop.tenantId(), "IN-2", "Insight Anvil", "3", 500000, 400000, "0");
        // Sold with the hammer every time.
        Long nails = insertProduct(shop.tenantId(), "IN-3", "Insight Nails", "100", 2000, 1500, "0");
        // Priced below cost.
        Long loss = insertProduct(shop.tenantId(), "IN-4", "Insight Loss Leader", "10", 10000, 12000, "0");

        // Before any sale: everything with stock is slow-moving; nothing is bought together.
        JsonNode slowBefore = insight(shop, "slow-moving?days=30");
        assertThat(slowBefore.path("items")).hasSize(4);
        assertThat(insight(shop, "bought-together?days=30").path("items")).isEmpty();
        assertThat(insight(shop, "bought-together?days=30").path("summary").asText()).contains("No two products");

        sell(shop, "9800005001", List.of(new InvoiceItemRequest(hammer, new BigDecimal("5")), new InvoiceItemRequest(nails, new BigDecimal("20"))));
        sell(shop, "9800005002", List.of(new InvoiceItemRequest(hammer, new BigDecimal("5")), new InvoiceItemRequest(nails, new BigDecimal("10"))));

        JsonNode slow = insight(shop, "slow-moving?days=30");
        List<String> slowNames = slow.path("items").findValuesAsText("productName");
        assertThat(slowNames).contains("Insight Anvil", "Insight Loss Leader").doesNotContain("Insight Hammer", "Insight Nails");
        JsonNode anvilRow = slow.path("items").get(0);
        assertThat(anvilRow.path("productName").asText()).isEqualTo("Insight Anvil");
        // 3 x 4,000.00 at cost
        assertThat(anvilRow.path("stockValueDisplay").asText()).isEqualTo("12,000.00");

        JsonNode reorder = insight(shop, "reorder?days=30&leadTimeDays=7");
        JsonNode hammerRow = reorder.path("items").get(0);
        assertThat(hammerRow.path("productName").asText()).isEqualTo("Insight Hammer");
        assertThat(hammerRow.path("quantityOnHand").asDouble()).isEqualTo(2.0);
        assertThat(hammerRow.path("suggestedQuantity").asDouble()).isGreaterThan(0);
        assertThat(reorder.path("items").findValuesAsText("productName")).doesNotContain("Insight Anvil");

        JsonNode together = insight(shop, "bought-together?days=30");
        assertThat(together.path("pairs")).isNotEmpty();
        JsonNode pair = together.path("pairs").get(0);
        assertThat(pair.path("invoicesTogether").asLong()).isEqualTo(2);
        assertThat(pair.path("supportPercent").asDouble()).isEqualTo(100.0);

        JsonNode pricing = insight(shop, "pricing?days=30");
        JsonNode lossRow = pricing.path("items").get(0);
        assertThat(lossRow.path("productName").asText()).isEqualTo("Insight Loss Leader");
        assertThat(lossRow.path("flag").asText()).isEqualTo("SELLING_BELOW_COST");
        assertThat(pricing.path("items").findValuesAsText("productName")).doesNotContain("Insight Hammer");

        JsonNode trend = insight(shop, "demand-trend?days=30");
        assertThat(trend.path("rising").findValuesAsText("productName")).contains("Insight Hammer", "Insight Nails");
        assertThat(trend.path("falling")).isEmpty();
    }

    @Test
    @DisplayName("a Basic shop is refused the whole feature, with the plan it needs named")
    void basicShopIsRefused() throws Exception {
        Shop basic = registerShop(SubscriptionTier.FREE);
        mockMvc.perform(get("/v1/insights/slow-moving").header("Authorization", basic.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"));
    }
}
