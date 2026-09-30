package com.hardware.erp.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.invoice.dto.InvoiceItemRequest;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.purchase.dto.PurchaseItemRequest;
import com.hardware.erp.purchase.dto.PurchaseRequest;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-091 Phase 6. The brief's own scenario: buy at Rs 1,000/unit, sell one
 * unit at Rs 1,500, THEN buy more stock at Rs 1,200/unit. The already-sold
 * unit's profit must still be computed against the Rs 1,000 it actually
 * cost when it left the shelf - never against whatever the product's
 * purchase price happens to be today. That freeze point is
 * InvoiceServiceImpl.applyGstSplitAndCost(), which reads stock's weighted
 * average cost at the moment of sale and copies it onto the invoice line;
 * AnalyticsRepository.profitFigures() then sums that frozen figure, never
 * product.purchase_price_paise.
 */
class ProfitHistoricalCostIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private record Shop(String bearer, Long tenantId) {}

    private Shop registerShop() throws Exception {
        String mobile = "9" + (100000000 + ThreadLocalRandom.current().nextInt(899999999));
        String email = "owner" + mobile + "@profittest.example";
        String body = mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(json(new TenantRegistrationRequest(
                                "Profit Test Shop " + mobile.substring(5), "Owner", mobile, email, "Profit@2026",
                                SubscriptionTier.MAX, true, "1.0", "1.0", false, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long tenantId = tree(body).path("data").path("tenantId").asLong();
        return new Shop(bearer(mobile, "Profit@2026"), tenantId);
    }

    private Long insertSupplier(Long tenantId, String code) {
        return jdbc.queryForObject("""
                INSERT INTO supplier (tenant_id, supplier_code, supplier_name, mobile_no, status, created_at)
                VALUES (?, ?, 'Historical Cost Supplier', '9811100001', 'ACTIVE', now())
                RETURNING supplier_id""", Long.class, tenantId, code);
    }

    private Long insertProductNoStock(Long tenantId, String code, String name) {
        return jdbc.queryForObject("""
                INSERT INTO product (tenant_id, product_code, product_name, unit, selling_price_paise,
                                     purchase_price_paise, mrp_paise, gst_rate_percent, status, created_at)
                VALUES (?, ?, ?, 'PCS', 150000, 80000, 200000, 0, 'ACTIVE', now())
                RETURNING product_id""", Long.class, tenantId, code, name);
    }

    @Test
    @DisplayName("a later purchase at a higher price does not change the profit already recorded on an earlier sale")
    void laterPriceChangeDoesNotAffectAlreadySoldUnitsCogs() throws Exception {
        Shop shop = registerShop();
        Long supplierId = insertSupplier(shop.tenantId(), "SUP-HC1");
        Long productId = insertProductNoStock(shop.tenantId(), "HC-1", "Historical Cost Item");

        // Buy 10 units at Rs 1,000/unit - stock's weighted average cost becomes 1,000.00.
        mockMvc.perform(post("/v1/purchases").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new PurchaseRequest(supplierId, "BILL-HC-1", LocalDate.now(),
                                List.of(new PurchaseItemRequest(productId, new BigDecimal("10"), 100000L, BigDecimal.ZERO)),
                                true, null, null, "Initial stock-in"))))
                .andExpect(status().isCreated());

        // Sell 1 unit at Rs 1,500 - the invoice line freezes cost_price_paise at today's average, 1,000.00.
        String invoiceBody = mockMvc.perform(post("/v1/invoices").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new InvoiceRequest("Historical Cost Customer", "9800002001", null, null, null,
                                List.of(new InvoiceItemRequest(productId, BigDecimal.ONE)),
                                null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(tree(invoiceBody).path("data").path("totalDisplay").asText()).isEqualTo("1,500.00");

        // Buy 5 MORE units at Rs 1,200/unit and let it overwrite the product's purchase price -
        // the very thing that must NOT reach back and change the profit already booked above.
        mockMvc.perform(post("/v1/purchases").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new PurchaseRequest(supplierId, "BILL-HC-2", LocalDate.now(),
                                List.of(new PurchaseItemRequest(productId, new BigDecimal("5"), 120000L, BigDecimal.ZERO)),
                                true, null, null, "Restock at a higher price"))))
                .andExpect(status().isCreated());

        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now().plusDays(1);
        String profitBody = mockMvc.perform(get("/v1/analytics/profit")
                        .param("from", from.toString()).param("to", to.toString())
                        .header("Authorization", shop.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode profit = tree(profitBody).path("data");

        assertThat(profit.path("revenuePaise").asLong()).isEqualTo(150000L);
        // COGS must reflect the Rs 1,000 the unit actually cost when sold, not the Rs 1,200
        // the product now costs to restock - 100000 paise, not 120000.
        assertThat(profit.path("cogsPaise").asLong()).isEqualTo(100000L);
        assertThat(profit.path("grossProfitPaise").asLong()).isEqualTo(50000L);
    }
}
