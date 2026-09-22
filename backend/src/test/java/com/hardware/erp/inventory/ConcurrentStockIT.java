package com.hardware.erp.inventory;

import com.hardware.erp.invoice.dto.InvoiceItemRequest;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.dto.PaymentRequest;
import com.hardware.erp.invoice.entity.PaymentMethod;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consolidation audit (2026-09-22). The brief's own race: stock = 1, two
 * users invoice it at once, both read 1 - exactly one may sell it.
 * StockRepository.lockByTenantIdAndProductId is PESSIMISTIC_WRITE, so the
 * second transaction waits on the row and then sees 0. Proven here with
 * eight real concurrent requests against PostgreSQL, not two mocked ones.
 * The same shape proves a payment cannot be double-recorded past the
 * total: Invoice carries @Version, so the loser's UPDATE fails and its
 * payment row rolls back with it.
 */
class ConcurrentStockIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private record Shop(String bearer, Long tenantId) {}

    private Shop registerShop() throws Exception {
        String mobile = "9" + (100000000 + ThreadLocalRandom.current().nextInt(899999999));
        String body = mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(json(new TenantRegistrationRequest("Race Shop " + mobile.substring(5), "Owner",
                                mobile, "owner" + mobile + "@racetest.example", "Race@2026",
                                SubscriptionTier.MAX, true, "1.0", "1.0", false, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Shop(bearer(mobile, "Race@2026"), tree(body).path("data").path("tenantId").asLong());
    }

    private Long insertProduct(Long tenantId, String stock) {
        Long productId = jdbc.queryForObject("""
                INSERT INTO product (tenant_id, product_code, product_name, unit, selling_price_paise,
                                     purchase_price_paise, mrp_paise, gst_rate_percent, status, created_at)
                VALUES (?, 'RACE-1', 'Last Unit', 'PCS', 100000, 60000, 100000, 0, 'ACTIVE', now())
                RETURNING product_id""", Long.class, tenantId);
        jdbc.update("INSERT INTO stock (tenant_id, product_id, quantity_on_hand, created_at) VALUES (?, ?, CAST(? AS DECIMAL), now())",
                tenantId, productId, stock);
        return productId;
    }

    private List<Integer> fire(int threads, java.util.concurrent.Callable<Integer> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> { go.await(); return call.call(); }));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) statuses.add(f.get());
        pool.shutdown();
        return statuses;
    }

    @Test
    @DisplayName("stock = 1, eight simultaneous invoices for it: exactly one succeeds, stock ends at 0, one movement")
    void oneUnitCannotBeSoldTwice() throws Exception {
        Shop shop = registerShop();
        Long productId = insertProduct(shop.tenantId(), "1");
        String body = json(new InvoiceRequest("Race Customer", "9800008001", null, null, null,
                List.of(new InvoiceItemRequest(productId, BigDecimal.ONE)), null, null, null));

        List<Integer> statuses = fire(8, () -> mockMvc.perform(post("/v1/invoices").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 422).hasSize(7);
        assertThat(jdbc.queryForObject("SELECT quantity_on_hand FROM stock WHERE tenant_id = ? AND product_id = ?",
                BigDecimal.class, shop.tenantId(), productId)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invoice WHERE tenant_id = ?", Integer.class, shop.tenantId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_movement WHERE tenant_id = ? AND reference_type = 'INVOICE'",
                Integer.class, shop.tenantId())).isEqualTo(1);
    }

    @Test
    @DisplayName("an invoice for 1,000 taking eight simultaneous 600 payments records at most one and never exceeds the total")
    void paymentsCannotOverpayConcurrently() throws Exception {
        Shop shop = registerShop();
        Long productId = insertProduct(shop.tenantId(), "5");
        String created = mockMvc.perform(post("/v1/invoices").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new InvoiceRequest("Pay Customer", "9800008002", null, null, null,
                                List.of(new InvoiceItemRequest(productId, BigDecimal.ONE)), null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long invoiceId = tree(created).path("data").path("id").asLong();
        String body = json(new PaymentRequest(60000L, PaymentMethod.CASH, "race"));

        List<Integer> statuses = fire(8, () -> mockMvc.perform(post("/v1/invoices/" + invoiceId + "/payments")
                        .header("Authorization", shop.bearer()).contentType(APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 422).hasSize(7);
        Long paid = jdbc.queryForObject("SELECT COALESCE(SUM(amount_paise), 0) FROM payment WHERE invoice_id = ?", Long.class, invoiceId);
        Long paidOnInvoice = jdbc.queryForObject("SELECT paid_paise FROM invoice WHERE invoice_id = ?", Long.class, invoiceId);
        assertThat(paid).isEqualTo(60000L);
        assertThat(paidOnInvoice).isEqualTo(60000L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_ledger_entry WHERE tenant_id = ? AND entry_type = 'PAYMENT'",
                Integer.class, shop.tenantId())).isEqualTo(1);
    }
}
