package com.hardware.erp.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.inventory.dto.StockAdjustmentRequest;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-070. The out-of-stock filter on GET /v1/stock.
 *
 * This is an integration test rather than a unit test on purpose: the whole
 * change is a JPQL predicate comparing a DECIMAL column to a literal, and a
 * mocked repository would assert only that a boolean was passed along - it
 * would pass just as happily if the query were wrong.
 *
 * The test drives a real product to zero and puts it back afterwards, so it
 * can run repeatedly against the reused container without accumulating state
 * (the lesson from ProjectMaterialStockIT: an IT that writes rows a later test
 * can see must be idempotent, not merely correct).
 */
class StockOutOfStockFilterIT extends AbstractIntegrationTest {

    private JsonNode stockPage(String token, String query) throws Exception {
        String body = mockMvc.perform(get("/v1/stock" + query).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data").path("content");
    }

    private void adjust(String token, long productId, BigDecimal change) throws Exception {
        mockMvc.perform(post("/v1/stock/" + productId + "/adjust")
                        .header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content(json(new StockAdjustmentRequest(change, "CR-070 filter test"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CR-070: outOfStockOnly returns only rows at or below zero, and leaves lowStockOnly alone")
    void outOfStockOnlyFiltersToZero() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);

        JsonNode all = stockPage(token, "?size=100");
        assertThat(all).as("seed data must contain stock rows").isNotEmpty();

        // Pick a product that currently has something on hand, so driving it to
        // zero is a real state change rather than a no-op.
        JsonNode target = null;
        for (JsonNode row : all) {
            if (new BigDecimal(row.path("quantityOnHand").asText()).signum() > 0) {
                target = row;
                break;
            }
        }
        assertThat(target).as("seed data must contain at least one in-stock product").isNotNull();

        long productId = target.path("productId").asLong();
        BigDecimal onHand = new BigDecimal(target.path("quantityOnHand").asText());

        // Before: it is in stock, so the out-of-stock list must not carry it.
        assertThat(idsOf(stockPage(token, "?outOfStockOnly=true&size=100")))
                .as("an in-stock product must not appear on the out-of-stock list")
                .doesNotContain(productId);

        adjust(token, productId, onHand.negate());
        try {
            JsonNode outOfStock = stockPage(token, "?outOfStockOnly=true&size=100");

            assertThat(idsOf(outOfStock))
                    .as("a product driven to zero must appear on the out-of-stock list")
                    .contains(productId);

            // The predicate itself, not just the one row we moved.
            for (JsonNode row : outOfStock) {
                assertThat(new BigDecimal(row.path("quantityOnHand").asText()).signum())
                        .as("every out-of-stock row must be at or below zero")
                        .isLessThanOrEqualTo(0);
            }

            // Zero is at or below any non-negative reorder level, so the row is
            // low as well as out - the two flags describe overlapping sets and
            // must not be made to exclude one another.
            assertThat(idsOf(stockPage(token, "?lowStockOnly=true&size=100")))
                    .as("a zero-quantity row is also low stock")
                    .contains(productId);

            // Both together AND rather than conflict: the result is the
            // out-of-stock set, never empty and never the whole list.
            JsonNode both = stockPage(token, "?lowStockOnly=true&outOfStockOnly=true&size=100");
            assertThat(idsOf(both)).contains(productId);
            for (JsonNode row : both) {
                assertThat(new BigDecimal(row.path("quantityOnHand").asText()).signum())
                        .isLessThanOrEqualTo(0);
            }
        } finally {
            // Put the shop back exactly as it was found.
            adjust(token, productId, onHand);
        }

        assertThat(idsOf(stockPage(token, "?outOfStockOnly=true&size=100")))
                .as("restoring the quantity must take it off the out-of-stock list again")
                .doesNotContain(productId);
    }

    @Test
    @DisplayName("CR-070: the default (no flag) is unchanged - every row still comes back")
    void defaultIsUnfiltered() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        assertThat(stockPage(token, "?size=100").size())
                .isEqualTo(stockPage(token, "?outOfStockOnly=false&size=100").size());
    }

    private static java.util.List<Long> idsOf(JsonNode content) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        content.forEach(row -> ids.add(row.path("productId").asLong()));
        return ids;
    }
}
