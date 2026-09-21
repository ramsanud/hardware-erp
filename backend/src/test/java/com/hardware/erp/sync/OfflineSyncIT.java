package com.hardware.erp.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.invoice.dto.InvoiceItemRequest;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.sync.entity.SyncTransactionType;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-091 Phase 9. A device that uploads the same offline sale twice - a
 * genuinely common case: the response to the first upload is lost to a
 * flaky connection, so the client retries with the same client-generated
 * UUID - must end up with exactly one invoice, one stock movement and one
 * customer ledger row, never two. The idempotency is by unique constraint
 * on (tenant_id, client_uuid) at the sync_transaction table plus the
 * existsBy... checks in StockService/CustomerLedgerService's own posting
 * paths - this test proves the whole chain, not just the sync row itself.
 */
class OfflineSyncIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private record Shop(String bearer, Long tenantId) {}

    private Shop registerShop() throws Exception {
        String mobile = "9" + (100000000 + ThreadLocalRandom.current().nextInt(899999999));
        String email = "owner" + mobile + "@synctest.example";
        String body = mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(json(new TenantRegistrationRequest(
                                "Sync Test Shop " + mobile.substring(5), "Owner", mobile, email, "Sync@2026",
                                SubscriptionTier.MAX, true, "1.0", "1.0", false))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long tenantId = tree(body).path("data").path("tenantId").asLong();
        return new Shop(bearer(mobile, "Sync@2026"), tenantId);
    }

    private Long insertProduct(Long tenantId, String code, String name, String stock) {
        Long productId = jdbc.queryForObject("""
                INSERT INTO product (tenant_id, product_code, product_name, unit, selling_price_paise,
                                     purchase_price_paise, mrp_paise, gst_rate_percent, status, created_at)
                VALUES (?, ?, ?, 'PCS', 50000, 30000, 60000, 0, 'ACTIVE', now())
                RETURNING product_id""", Long.class, tenantId, code, name);
        jdbc.update("INSERT INTO stock (tenant_id, product_id, quantity_on_hand, created_at) VALUES (?, ?, CAST(? AS DECIMAL), now())",
                tenantId, productId, stock);
        return productId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payloadFor(InvoiceRequest request) {
        return objectMapper.convertValue(request, Map.class);
    }

    @Test
    @DisplayName("re-uploading the same offline invoice with the same client UUID creates exactly one invoice, stock movement and ledger row")
    void duplicateUploadIsANoOp() throws Exception {
        Shop shop = registerShop();
        Long productId = insertProduct(shop.tenantId(), "OS-1", "Offline Sync Item", "20");

        InvoiceRequest invoiceRequest = new InvoiceRequest("Offline Customer", "9800003001", null, null, null,
                List.of(new InvoiceItemRequest(productId, new BigDecimal("3"))),
                null, null, "Recorded offline");
        UUID clientUuid = UUID.randomUUID();
        Map<String, Object> transaction = Map.of(
                "clientUuid", clientUuid.toString(),
                "deviceId", "TEST-DEVICE-001",
                "transactionType", SyncTransactionType.INVOICE.name(),
                "clientCreatedAt", LocalDateTime.now().minusMinutes(30).toString(),
                "payload", payloadFor(invoiceRequest));
        Map<String, Object> batch = Map.of("transactions", List.of(transaction));

        String firstBody = mockMvc.perform(post("/v1/sync/transactions").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(batch)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode firstResult = tree(firstBody).path("data").path("results").get(0);
        assertThat(firstResult.path("status").asText()).isEqualTo("SYNCED");
        assertThat(firstResult.path("replay").asBoolean()).isFalse();
        Long invoiceId = firstResult.path("resultReferenceId").asLong();

        // The exact same batch, same client UUID, sent again - a real retry.
        String secondBody = mockMvc.perform(post("/v1/sync/transactions").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(batch)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode secondResult = tree(secondBody).path("data").path("results").get(0);
        assertThat(secondResult.path("status").asText()).isEqualTo("SYNCED");
        assertThat(secondResult.path("replay").asBoolean()).isTrue();
        assertThat(secondResult.path("resultReferenceId").asLong()).isEqualTo(invoiceId);

        Integer invoiceCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoice WHERE tenant_id = ? AND invoice_id = ?",
                Integer.class, shop.tenantId(), invoiceId);
        assertThat(invoiceCount).isEqualTo(1);

        Integer movementCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_movement WHERE tenant_id = ? AND reference_type = 'INVOICE' AND reference_id = ?",
                Integer.class, shop.tenantId(), invoiceId);
        assertThat(movementCount).isEqualTo(1);

        Integer ledgerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer_ledger_entry WHERE tenant_id = ? AND reference_type = 'INVOICE' AND reference_id = ?",
                Integer.class, shop.tenantId(), invoiceId);
        assertThat(ledgerCount).isEqualTo(1);

        Integer syncRowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sync_transaction WHERE tenant_id = ? AND client_uuid = ?",
                Integer.class, shop.tenantId(), clientUuid);
        assertThat(syncRowCount).isEqualTo(1);

        Integer stockOnHand = jdbc.queryForObject(
                "SELECT quantity_on_hand::int FROM stock WHERE tenant_id = ? AND product_id = ?",
                Integer.class, shop.tenantId(), productId);
        // 20 - 3 = 17, decremented exactly once, not twice.
        assertThat(stockOnHand).isEqualTo(17);
    }

    @Test
    @DisplayName("a genuinely new offline sale (different client UUID) after a stock-changing conflict is recorded independently")
    void insufficientStockAtSyncTimeIsAConflictNotASilentRetry() throws Exception {
        Shop shop = registerShop();
        Long productId = insertProduct(shop.tenantId(), "OS-2", "Low Stock Offline Item", "2");

        InvoiceRequest tooMany = new InvoiceRequest("Offline Customer Two", "9800003002", null, null, null,
                List.of(new InvoiceItemRequest(productId, new BigDecimal("5"))),
                null, null, "Offline sale exceeding current stock");
        Map<String, Object> transaction = Map.of(
                "clientUuid", UUID.randomUUID().toString(),
                "deviceId", "TEST-DEVICE-002",
                "transactionType", SyncTransactionType.INVOICE.name(),
                "clientCreatedAt", LocalDateTime.now().minusMinutes(10).toString(),
                "payload", payloadFor(tooMany));
        Map<String, Object> batch = Map.of("transactions", List.of(transaction));

        String body = mockMvc.perform(post("/v1/sync/transactions").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(batch)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode result = tree(body).path("data").path("results").get(0);
        assertThat(result.path("status").asText()).isEqualTo("CONFLICT");
        assertThat(result.path("conflictReason").asText()).isNotBlank();

        Integer invoiceCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoice WHERE tenant_id = ?", Integer.class, shop.tenantId());
        assertThat(invoiceCount).isEqualTo(0);
    }
}
