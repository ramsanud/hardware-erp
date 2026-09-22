package com.hardware.erp.invoice;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.invoice.dto.InvoiceItemRequest;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.dto.PaymentRequest;
import com.hardware.erp.invoice.entity.PaymentMethod;
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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-102. The financial endpoints that create money or stock rows -
 * invoice, invoice payment, purchase, purchase payment - now honour an
 * Idempotency-Key exactly as credit notes have since CR-051. A request
 * whose response was lost and is retried with the same key must not
 * create a second document, a second payment, or a second stock movement.
 */
class FinancialIdempotencyIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private record Shop(String bearer, Long tenantId) {}

    private Shop registerShop() throws Exception {
        String mobile = "9" + (100000000 + ThreadLocalRandom.current().nextInt(899999999));
        String body = mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(json(new TenantRegistrationRequest("Idempotency Shop " + mobile.substring(5), "Owner",
                                mobile, "owner" + mobile + "@idemtest.example", "Idem@2026",
                                SubscriptionTier.MAX, true, "1.0", "1.0", false, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Shop(bearer(mobile, "Idem@2026"), tree(body).path("data").path("tenantId").asLong());
    }

    private Long insertProduct(Long tenantId, String code, String stock) {
        Long productId = jdbc.queryForObject("""
                INSERT INTO product (tenant_id, product_code, product_name, unit, selling_price_paise,
                                     purchase_price_paise, mrp_paise, gst_rate_percent, status, created_at)
                VALUES (?, ?, 'Idempotency Item', 'PCS', 100000, 60000, 100000, 0, 'ACTIVE', now())
                RETURNING product_id""", Long.class, tenantId, code);
        jdbc.update("INSERT INTO stock (tenant_id, product_id, quantity_on_hand, created_at) VALUES (?, ?, CAST(? AS DECIMAL), now())",
                tenantId, productId, stock);
        return productId;
    }

    private BigDecimal onHand(Long tenantId, Long productId) {
        return jdbc.queryForObject("SELECT quantity_on_hand FROM stock WHERE tenant_id = ? AND product_id = ?",
                BigDecimal.class, tenantId, productId);
    }

    @Test
    @DisplayName("the same invoice request retried with the same key creates one invoice, one movement, one ledger row")
    void invoiceRetryIsANoOp() throws Exception {
        Shop shop = registerShop();
        Long productId = insertProduct(shop.tenantId(), "ID-1", "10");
        String key = UUID.randomUUID().toString();
        String payload = json(new InvoiceRequest("Idempotent Customer", "9800007001", null, null, null,
                List.of(new InvoiceItemRequest(productId, new BigDecimal("2"))), null, null, null));

        String first = mockMvc.perform(post("/v1/invoices").header("Authorization", shop.bearer())
                        .header("Idempotency-Key", key).contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/v1/invoices").header("Authorization", shop.bearer())
                        .header("Idempotency-Key", key).contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode a = tree(first).path("data"), b = tree(second).path("data");
        assertThat(b.path("id").asLong()).isEqualTo(a.path("id").asLong());
        assertThat(b.path("invoiceNumber").asText()).isEqualTo(a.path("invoiceNumber").asText());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invoice WHERE tenant_id = ?", Integer.class, shop.tenantId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_movement WHERE tenant_id = ? AND reference_type = 'INVOICE'", Integer.class, shop.tenantId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_ledger_entry WHERE tenant_id = ?", Integer.class, shop.tenantId())).isEqualTo(1);
        assertThat(onHand(shop.tenantId(), productId)).isEqualByComparingTo("8");

        // Same key, different request: refused, not silently served from cache.
        mockMvc.perform(post("/v1/invoices").header("Authorization", shop.bearer())
                        .header("Idempotency-Key", key).contentType(APPLICATION_JSON)
                        .content(json(new InvoiceRequest("Idempotent Customer", "9800007001", null, null, null,
                                List.of(new InvoiceItemRequest(productId, new BigDecimal("3"))), null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(onHand(shop.tenantId(), productId)).isEqualByComparingTo("8");

        // A payment retried with its own key is recorded once.
        Long invoiceId = a.path("id").asLong();
        String payKey = UUID.randomUUID().toString();
        String payBody = json(new PaymentRequest(50000L, PaymentMethod.CASH, "First half"));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/v1/invoices/" + invoiceId + "/payments").header("Authorization", shop.bearer())
                            .header("Idempotency-Key", payKey).contentType(APPLICATION_JSON).content(payBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.paidDisplay").value("500.00"));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE tenant_id = ?", Integer.class, shop.tenantId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_ledger_entry WHERE tenant_id = ? AND entry_type = 'PAYMENT'", Integer.class, shop.tenantId())).isEqualTo(1);
    }

    @Test
    @DisplayName("the same purchase retried with the same key is received once")
    void purchaseRetryIsANoOp() throws Exception {
        Shop shop = registerShop();
        Long productId = insertProduct(shop.tenantId(), "ID-2", "0");
        Long supplierId = jdbc.queryForObject("""
                INSERT INTO supplier (tenant_id, supplier_code, supplier_name, mobile_no, status, created_at)
                VALUES (?, 'SUP-ID1', 'Idempotent Supplier', '9811100003', 'ACTIVE', now()) RETURNING supplier_id""",
                Long.class, shop.tenantId());
        String key = UUID.randomUUID().toString();
        String payload = json(new PurchaseRequest(supplierId, "BILL-ID-1", LocalDate.now(),
                List.of(new PurchaseItemRequest(productId, new BigDecimal("5"), 60000L, BigDecimal.ZERO)),
                false, null, null, null));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/v1/purchases").header("Authorization", shop.bearer())
                            .header("Idempotency-Key", key).contentType(APPLICATION_JSON).content(payload))
                    .andExpect(status().isCreated());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase WHERE tenant_id = ?", Integer.class, shop.tenantId())).isEqualTo(1);
        assertThat(onHand(shop.tenantId(), productId)).isEqualByComparingTo("5");
    }
}
