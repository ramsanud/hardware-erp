package com.hardware.erp.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.product.dto.ProductRequest;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for BUG-BE-003 - reading the stock of a product that has no
 * stock row returned 500.
 *
 * StockServiceImpl creates the (tenant, product) stock row "lazily on first
 * access". That lazy save() was reached from get() and movements(), which are
 * both {@code @Transactional(readOnly = true)}; PostgreSQL rejects an INSERT in
 * a read-only transaction, so the endpoint answered INTERNAL_ERROR for every
 * product whose row did not exist yet - which is every product from the moment
 * it is created until its first stock movement.
 *
 * <p><b>Why the existing suite could not have caught this.</b> The dev/test
 * seed inserts opening stock for every product it creates, with the comment
 * "not a Stock row created lazily at zero on first API call". So every product
 * any previous test could reach already had a row, and the lazy-creation branch
 * was never entered through a read. This test therefore creates its own
 * product - which is also the real-world path: add a product, open it, 500.
 *
 * <p>It has to be an integration test. The defect is a database refusing a
 * write inside a read-only transaction; a mocked StockRepository has no
 * transaction and no opinion about writes, and would happily return a saved row
 * against the broken code. The companion unit tests in StockServiceImplTest
 * assert the rule one level down (a read never calls save()).
 *
 * <p>Idempotent against the reused container: the product it creates is deleted
 * in the same test.
 */
class StockReadWithoutRowIT extends AbstractIntegrationTest {

    private static ProductRequest newProduct(String suffix) {
        return new ProductRequest(
                null,                       // let the service generate the code
                "BUG-BE-003 probe " + suffix,
                null, null, null, null, null,
                "PCS",
                null, null,
                new BigDecimal("18.00"),
                10000L, 15000L, 20000L,
                new BigDecimal("5"),
                new BigDecimal("10"),
                ProductStatus.ACTIVE,
                null, null);
    }

    /** Creates a product, which by definition has no stock row yet. */
    private long createProduct(String token) throws Exception {
        String body = mockMvc.perform(post("/v1/products")
                        .header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content(json(newProduct(String.valueOf(System.nanoTime())))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data").path("id").asLong();
    }

    private JsonNode content(String token, String url) throws Exception {
        String body = mockMvc.perform(get(url).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data").path("content");
    }

    private static List<Long> productIds(JsonNode content) {
        List<Long> out = new ArrayList<>();
        content.forEach(row -> out.add(row.path("productId").asLong()));
        return out;
    }

    @Test
    @DisplayName("BUG-BE-003: stock of a product with no stock row reads as zero, not 500")
    void readsZeroInsteadOfFailing() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        long productId = createProduct(token);
        try {
            String body = mockMvc.perform(get("/v1/stock/" + productId).header("Authorization", token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode data = tree(body).path("data");
            assertThat(data.path("productId").asLong())
                    .as("the response must describe the product that was asked for")
                    .isEqualTo(productId);
            assertThat(new BigDecimal(data.path("quantityOnHand").asText()).signum())
                    .as("a product with no stock row holds nothing")
                    .isZero();
            assertThat(data.path("productCode").asText())
                    .as("the product half of the response is still populated")
                    .isNotEmpty();
            assertThat(data.path("lowStock").asBoolean())
                    .as("nothing on hand against a reorder level of 10 is low stock")
                    .isTrue();
        } finally {
            mockMvc.perform(delete("/v1/products/" + productId).header("Authorization", token));
        }
    }

    @Test
    @DisplayName("BUG-BE-003: movements of a product with no stock row is an empty page, not 500")
    void movementsReadEmptyInsteadOfFailing() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        long productId = createProduct(token);
        try {
            assertThat(content(token, "/v1/stock/" + productId + "/movements"))
                    .as("a product that never moved has no movements")
                    .isEmpty();
        } finally {
            mockMvc.perform(delete("/v1/products/" + productId).header("Authorization", token));
        }
    }

    /**
     * The half that would have gone unnoticed. Dropping readOnly would also
     * turn the 500 into a 200, while leaving a GET that writes to the database -
     * so assert the absence of the write, not merely the status code.
     */
    @Test
    @DisplayName("BUG-BE-003: reading stock does not create the row it was reading")
    void readingDoesNotCreateTheRow() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        long productId = createProduct(token);
        try {
            mockMvc.perform(get("/v1/stock/" + productId).header("Authorization", token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/v1/stock/" + productId + "/movements").header("Authorization", token))
                    .andExpect(status().isOk());

            // GET /v1/stock selects `from Stock`, so a product missing from it
            // is a product with no persisted row.
            assertThat(productIds(content(token, "/v1/stock?size=200")))
                    .as("a GET must not have persisted a stock row")
                    .doesNotContain(productId);
        } finally {
            mockMvc.perform(delete("/v1/products/" + productId).header("Authorization", token));
        }
    }

    @Test
    @DisplayName("BUG-BE-003: a product this tenant does not own is still 404, not 200 with zeroes")
    void unknownProductStill404() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);

        mockMvc.perform(get("/v1/stock/999999").header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/stock/999999/movements").header("Authorization", token))
                .andExpect(status().isNotFound());
    }
}
