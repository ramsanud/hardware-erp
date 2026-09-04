package com.hardware.erp.product.controller;

import com.hardware.erp.product.dto.ProductRequest;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Module 3's first controller integration test, added with CR-058 and scoped
 * to it: the deleted-records and restore surface, against real PostgreSQL with
 * the V902 seed products.
 *
 * These prove what a mocked repository cannot - that the native queries
 * genuinely see past Product's {@code @SQLRestriction("deleted_at is null")},
 * and that the restriction still hides deleted rows from every ordinary query
 * afterwards. Further Product coverage belongs in this class as it is written.
 */
class ProductControllerIT extends AbstractIntegrationTest {

    private String owner() throws Exception {
        return bearer(OWNER_MOBILE, OWNER_PASSWORD);
    }

    private ProductRequest product(String code, String name) {
        return new ProductRequest(code, name, null, null, null, null, null, "PCS",
                null, "8301", new BigDecimal("18.00"), 10_000L, 15_000L, 18_000L,
                BigDecimal.ZERO, BigDecimal.ZERO, ProductStatus.ACTIVE, null, null);
    }

    @Test
    @DisplayName("CR-058: delete then restore returns the same product row, id and code intact")
    void restoreLifecycle() throws Exception {
        String created = mockMvc.perform(post("/v1/products").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(product("PRD-900001", "Restore Me Hammer"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = tree(created).path("data").path("id").asLong();

        mockMvc.perform(delete("/v1/products/" + id).header("Authorization", owner()))
                .andExpect(status().isNoContent());

        // Hidden from GET-by-id and from search - @SQLRestriction still holds.
        mockMvc.perform(get("/v1/products/" + id).header("Authorization", owner()))
                .andExpect(status().isNotFound());
        String hidden = mockMvc.perform(get("/v1/products").header("Authorization", owner())
                        .param("search", "Restore Me Hammer"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(tree(hidden).path("data").path("totalElements").asInt()).isZero();

        // ... but visible in the recycle bin, with its deletion date.
        String deleted = mockMvc.perform(get("/v1/products/deleted").header("Authorization", owner()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(deleted).contains("Restore Me Hammer");
        assertThat(deleted).contains("deletedAt");
        // The reduced projection carries no price at all, cost or selling.
        assertThat(deleted).doesNotContain("sellingPrice");
        assertThat(deleted).doesNotContain("purchasePrice");

        mockMvc.perform(post("/v1/products/" + id + "/restore").header("Authorization", owner()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/products/" + id).header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value((int) id))
                .andExpect(jsonPath("$.data.productCode").value("PRD-900001"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                // Restore must not disturb anything but the delete columns and status.
                .andExpect(jsonPath("$.data.sellingPricePaise").value(15000))
                .andExpect(jsonPath("$.data.hsnCode").value("8301"));

        String afterRestore = mockMvc.perform(get("/v1/products").header("Authorization", owner())
                        .param("search", "Restore Me Hammer"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(tree(afterRestore).path("data").path("totalElements").asInt()).isEqualTo(1);

        mockMvc.perform(get("/v1/products/deleted").header("Authorization", owner()))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Restore Me Hammer"))));

        // Restoring twice matches nothing the second time.
        mockMvc.perform(post("/v1/products/" + id + "/restore").header("Authorization", owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("CR-058: a restored product can still be found by its code, so re-creating it is not needed")
    void restoredProductKeepsItsIdentity() throws Exception {
        String created = mockMvc.perform(post("/v1/products").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(product("PRD-900002", "Identity Kept Drill"))))
                .andReturn().getResponse().getContentAsString();
        long id = tree(created).path("data").path("id").asLong();

        mockMvc.perform(delete("/v1/products/" + id).header("Authorization", owner()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/v1/products/" + id + "/restore").header("Authorization", owner()))
                .andExpect(status().isNoContent());

        // The same row, not a duplicate: searching the code finds exactly one.
        String body = mockMvc.perform(get("/v1/products").header("Authorization", owner())
                        .param("search", "PRD-900002"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(tree(body).path("data").path("totalElements").asInt()).isEqualTo(1);
        assertThat(tree(body).path("data").path("content").get(0).path("id").asLong()).isEqualTo(id);
    }

    @Test
    @DisplayName("CR-058: restoring a product that was never deleted gives 404")
    void restoreOfLiveProductIsNotFound() throws Exception {
        String body = mockMvc.perform(get("/v1/products").header("Authorization", owner())
                        .param("search", "PRD-000010"))
                .andReturn().getResponse().getContentAsString();
        long liveId = tree(body).path("data").path("content").get(0).path("id").asLong();

        mockMvc.perform(post("/v1/products/" + liveId + "/restore").header("Authorization", owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("CR-058: restoring an unknown id gives 404, indistinguishable from the above")
    void restoreOfUnknownIdIsNotFound() throws Exception {
        mockMvc.perform(post("/v1/products/999999/restore").header("Authorization", owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("CR-058: PRODUCT_VIEW alone cannot see the deleted list or restore - both need PRODUCT_MANAGE")
    void deletedRecordsRequireManage() throws Exception {
        // STAFF holds PRODUCT_VIEW (it must, to serve the counter) but not
        // PRODUCT_MANAGE, which is exactly the boundary being asserted.
        String staff = bearer(STAFF_MOBILE, STAFF_PASSWORD);

        mockMvc.perform(get("/v1/products").header("Authorization", staff))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/products/deleted").header("Authorization", staff))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/v1/products/1/restore").header("Authorization", staff))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("CR-058: an unauthenticated caller cannot reach the deleted list or restore")
    void deletedRecordsRejectAnonymous() throws Exception {
        mockMvc.perform(get("/v1/products/deleted"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/products/1/restore"))
                .andExpect(status().isUnauthorized());
    }
}
