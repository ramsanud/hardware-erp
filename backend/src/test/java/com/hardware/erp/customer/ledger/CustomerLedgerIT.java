package com.hardware.erp.customer.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.invoice.dto.InvoiceItemRequest;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.entity.PaymentMethod;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-091 Phase 4. The brief's own worked example, run against real
 * PostgreSQL end to end through the real HTTP surface: a Rs 10,000 invoice
 * with a Rs 3,000 initial payment leaves Rs 7,000 outstanding; a further
 * Rs 2,000 payment leaves Rs 5,000; a Rs 1,000 sales return (one unit
 * credited back) leaves Rs 4,000.
 *
 * The product is priced at exactly Rs 1,000/unit with 0% GST so every
 * figure in the brief lines up without a GST-split detour - that detour is
 * already covered by GstSplitTest and the invoice/credit-note unit tests.
 */
class CustomerLedgerIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private record Shop(String bearer, Long tenantId) {}

    private Shop registerShop() throws Exception {
        String mobile = "9" + (100000000 + ThreadLocalRandom.current().nextInt(899999999));
        String email = "owner" + mobile + "@ledgertest.example";
        String body = mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(json(new TenantRegistrationRequest(
                                "Ledger Test Shop " + mobile.substring(5), "Owner", mobile, email, "Ledger@2026",
                                SubscriptionTier.MAX, true, "1.0", "1.0", false))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long tenantId = tree(body).path("data").path("tenantId").asLong();
        return new Shop(bearer(mobile, "Ledger@2026"), tenantId);
    }

    private Long insertProduct(Long tenantId, String code, String name, String stock) {
        Long productId = jdbc.queryForObject("""
                INSERT INTO product (tenant_id, product_code, product_name, unit, selling_price_paise,
                                     purchase_price_paise, mrp_paise, gst_rate_percent, status, created_at)
                VALUES (?, ?, ?, 'PCS', 100000, 80000, 120000, 0, 'ACTIVE', now())
                RETURNING product_id""", Long.class, tenantId, code, name);
        jdbc.update("INSERT INTO stock (tenant_id, product_id, quantity_on_hand, created_at) VALUES (?, ?, CAST(? AS DECIMAL), now())",
                tenantId, productId, stock);
        return productId;
    }

    private JsonNode balance(Shop shop, Long customerId) throws Exception {
        String body = mockMvc.perform(get("/v1/customers/" + customerId + "/ledger/balance")
                        .header("Authorization", shop.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data");
    }

    @Test
    @DisplayName("invoice, payment, further payment and a sales return each move the customer's outstanding balance correctly")
    void ledgerFollowsTheBriefsWorkedExample() throws Exception {
        Shop shop = registerShop();
        Long productId = insertProduct(shop.tenantId(), "LD-1", "Ledger Test Item", "50");

        String invoiceBody = mockMvc.perform(post("/v1/invoices").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new InvoiceRequest("Ledger Customer", "9800001001", null, null, null,
                                List.of(new InvoiceItemRequest(productId, new BigDecimal("10"))),
                                300000L, PaymentMethod.CASH, "Initial sale"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode invoice = tree(invoiceBody).path("data");
        assertThat(invoice.path("totalDisplay").asText()).isEqualTo("10,000.00");
        Long invoiceId = invoice.path("id").asLong();
        Long customerId = invoice.path("customerId").asLong();
        Long invoiceItemId = invoice.path("items").get(0).path("id").asLong();

        JsonNode afterInvoice = balance(shop, customerId);
        assertThat(afterInvoice.path("balancePaise").asLong()).isEqualTo(700000L);
        assertThat(afterInvoice.path("balanceDisplay").asText()).isEqualTo("7,000.00");

        mockMvc.perform(post("/v1/invoices/" + invoiceId + "/payments").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new com.hardware.erp.invoice.dto.PaymentRequest(200000L, PaymentMethod.UPI, "Second payment"))))
                .andExpect(status().isOk());

        JsonNode afterSecondPayment = balance(shop, customerId);
        assertThat(afterSecondPayment.path("balancePaise").asLong()).isEqualTo(500000L);
        assertThat(afterSecondPayment.path("balanceDisplay").asText()).isEqualTo("5,000.00");

        mockMvc.perform(post("/v1/credit-notes").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new com.hardware.erp.creditnote.dto.CreditNoteRequest(invoiceId,
                                List.of(new com.hardware.erp.creditnote.dto.CreditNoteItemRequest(invoiceItemId, new BigDecimal("1"))),
                                "Customer returned one unit", null))))
                .andExpect(status().isCreated());

        JsonNode afterReturn = balance(shop, customerId);
        assertThat(afterReturn.path("balancePaise").asLong()).isEqualTo(400000L);
        assertThat(afterReturn.path("balanceDisplay").asText()).isEqualTo("4,000.00");

        String statementBody = mockMvc.perform(get("/v1/customers/" + customerId + "/ledger/statement")
                        .param("from", "2020-01-01").param("to", "2030-01-01")
                        .header("Authorization", shop.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode statement = tree(statementBody).path("data");
        // INVOICE debit, the initial payment posted inline with invoice creation,
        // the second payment, and the sales return - four rows in all.
        assertThat(statement.path("entries")).hasSize(4);
        assertThat(statement.path("closingBalancePaise").asLong()).isEqualTo(400000L);
    }

    @Test
    @DisplayName("a manual ledger adjustment requires a reason and moves the balance in the stated direction")
    void manualAdjustmentRequiresReasonAndMovesBalance() throws Exception {
        Shop shop = registerShop();
        Long productId = insertProduct(shop.tenantId(), "LD-2", "Ledger Adjustment Item", "20");

        String invoiceBody = mockMvc.perform(post("/v1/invoices").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new InvoiceRequest("Adjustment Customer", "9800001002", null, null, null,
                                List.of(new InvoiceItemRequest(productId, new BigDecimal("2"))),
                                null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long customerId = tree(invoiceBody).path("data").path("customerId").asLong();

        assertThat(balance(shop, customerId).path("balancePaise").asLong()).isEqualTo(200000L);

        mockMvc.perform(post("/v1/customers/" + customerId + "/ledger/adjust")
                        .header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new LedgerDtos.LedgerAdjustmentRequest(50000L, false, "Goodwill discount"))))
                .andExpect(status().isOk());

        assertThat(balance(shop, customerId).path("balancePaise").asLong()).isEqualTo(150000L);

        mockMvc.perform(post("/v1/customers/" + customerId + "/ledger/adjust")
                        .header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new LedgerDtos.LedgerAdjustmentRequest(10000L, false, ""))))
                .andExpect(status().isBadRequest());
    }
}
