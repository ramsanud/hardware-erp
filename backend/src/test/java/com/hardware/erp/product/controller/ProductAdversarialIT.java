package com.hardware.erp.product.controller;

import com.hardware.erp.product.dto.ProductRequest;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consolidation audit (2026-09-22): the product validation table from the
 * enterprise brief, run as real requests. Every row here is a claim the
 * brief makes about what the API rejects; each is now proven rather than
 * assumed.
 */
class ProductAdversarialIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private static String code() {
        return "ADV-" + ThreadLocalRandom.current().nextInt(100000, 999999);
    }

    private static ProductRequest valid(String code, String name) {
        return new ProductRequest(code, name, null, null, null, null, null, "PCS", null, "7318",
                new BigDecimal("18.00"), 4_000L, 6_500L, 8_000L, BigDecimal.ZERO, BigDecimal.ZERO,
                ProductStatus.ACTIVE, null, null, null, null, null, null, null, null, null);
    }

    private static ProductRequest with(String code, String name, Long categoryId, Long brandId, BigDecimal gst,
                                       Long purchase, Long selling) {
        return new ProductRequest(code, name, categoryId, brandId, null, null, null, "PCS", null, "7318",
                gst, purchase, selling, 8_000L, BigDecimal.ZERO, BigDecimal.ZERO,
                ProductStatus.ACTIVE, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("a valid product is created; blank name, negative prices, GST over 100 and an empty body are 400")
    void validationTable() throws Exception {
        String owner = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        mockMvc.perform(post("/v1/products").header("Authorization", owner).contentType(APPLICATION_JSON)
                        .content(json(valid(code(), "Adversarial Valid Product"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/products").header("Authorization", owner).contentType(APPLICATION_JSON)
                        .content(json(valid(code(), "   "))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.productName").exists());
        mockMvc.perform(post("/v1/products").header("Authorization", owner).contentType(APPLICATION_JSON)
                        .content(json(with(code(), "Negative purchase", null, null, new BigDecimal("18"), -1L, 6_500L))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.purchasePricePaise").exists());
        mockMvc.perform(post("/v1/products").header("Authorization", owner).contentType(APPLICATION_JSON)
                        .content(json(with(code(), "Negative selling", null, null, new BigDecimal("18"), 4_000L, -1L))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.sellingPricePaise").exists());
        mockMvc.perform(post("/v1/products").header("Authorization", owner).contentType(APPLICATION_JSON)
                        .content(json(with(code(), "GST 150", null, null, new BigDecimal("150"), 4_000L, 6_500L))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.gstRatePercent").exists());
        mockMvc.perform(post("/v1/products").header("Authorization", owner).contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/products").header("Authorization", owner).contentType(APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/products").header("Authorization", owner).contentType(APPLICATION_JSON)
                        .content(json(valid(code(), "N".repeat(256)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.productName").exists());
    }

    @Test
    @DisplayName("a duplicate code, an unknown category and an unknown brand are refused without creating anything")
    void referencesAndDuplicates() throws Exception {
        String owner = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        String code = code();
        mockMvc.perform(post("/v1/products").header("Authorization", owner).contentType(APPLICATION_JSON)
                        .content(json(valid(code, "Original"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/products").header("Authorization", owner).contentType(APPLICATION_JSON)
                        .content(json(valid(code, "Duplicate of original"))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/v1/products").header("Authorization", owner).contentType(APPLICATION_JSON)
                        .content(json(with(code(), "Ghost category", 999_999L, null, new BigDecimal("18"), 4_000L, 6_500L))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/v1/products").header("Authorization", owner).contentType(APPLICATION_JSON)
                        .content(json(with(code(), "Ghost brand", null, 999_999L, new BigDecimal("18"), 4_000L, 6_500L))))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_code = ?", Integer.class, code)).isEqualTo(1);
    }

    @Test
    @DisplayName("no token, a garbage token, and a staff token without PRODUCT_MANAGE are all refused")
    void authorization() throws Exception {
        mockMvc.perform(post("/v1/products").contentType(APPLICATION_JSON).content(json(valid(code(), "No token"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/products").header("Authorization", "Bearer not.a.jwt").contentType(APPLICATION_JSON)
                        .content(json(valid(code(), "Bad token"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/products").header("Authorization", bearer(STAFF_MOBILE, STAFF_PASSWORD))
                        .contentType(APPLICATION_JSON).content(json(valid(code(), "Staff cannot"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("eight concurrent creates with the same code yield exactly one product")
    void concurrentDuplicateCode() throws Exception {
        String owner = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        String code = code();
        String body = json(valid(code, "Raced product"));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return mockMvc.perform(post("/v1/products").header("Authorization", owner)
                                .contentType(APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        int created = 0;
        for (Future<Integer> f : results) {
            int status = f.get();
            assertThat(status).isIn(201, 409, 500);
            if (status == 201) created++;
        }
        pool.shutdown();
        assertThat(created).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_code = ?", Integer.class, code)).isEqualTo(1);
    }
}
