package com.hardware.erp.quotation;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.quotation.entity.Quotation;
import com.hardware.erp.quotation.repository.QuotationRepository;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-083, and the regression test for BUG-BE-005.
 *
 * <p>EXPIRED is computed from validUntil and never stored (CR-022), so the
 * list's {@code ?status=EXPIRED} filter - which compared the stored column -
 * could not match a single row from the day it shipped, while the badge on
 * every one of those rows said "Expired". This test has to run against the
 * real query: a mocked repository has no opinion about JPQL.
 *
 * <p>The API refuses a validUntil in the past ({@code @Future}), which is the
 * only reason the repository is touched directly here: it is how a quotation
 * becomes expired in real life - time passes - compressed into one line.
 *
 * <p>Each test scopes its reads with a search key unique to the run, so the
 * reused container's other quotations never leak into an assertion, and the
 * draft it leaves behind is the one that DELETE must refuse.
 */
class QuotationListFiltersIT extends AbstractIntegrationTest {

    @Autowired private QuotationRepository quotationRepository;

    private long firstProductId(String token) throws Exception {
        String body = mockMvc.perform(get("/v1/products?size=1").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data").path("content").get(0).path("id").asLong();
    }

    private long createDraft(String token, String customerName, String mobile, long productId) throws Exception {
        Map<String, Object> request = Map.of(
                "customerName", customerName,
                "customerMobile", mobile,
                "validUntil", LocalDate.now().plusDays(7).toString(),
                "items", List.of(Map.of("productId", productId, "quantity", 2)));
        String body = mockMvc.perform(post("/v1/quotations")
                        .header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data").path("id").asLong();
    }

    /** Time passes: the quotation's valid-until is now yesterday. */
    private void expire(long id) {
        Quotation quotation = quotationRepository.findById(id).orElseThrow();
        quotation.setValidUntil(LocalDate.now().minusDays(1));
        quotationRepository.save(quotation);
    }

    private List<Long> listIds(String token, String query) throws Exception {
        String body = mockMvc.perform(get("/v1/quotations?" + query).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        tree(body).path("data").path("content").forEach(row -> ids.add(row.path("id").asLong()));
        return ids;
    }

    @Test
    @DisplayName("BUG-BE-005: the Expired filter finds an expired draft, and the Draft filter no longer does")
    void expiredFilterMatchesComputedExpiry() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        String key = "Filter probe " + System.nanoTime();
        long productId = firstProductId(token);
        long live = createDraft(token, key, "9876540001", productId);
        long expired = createDraft(token, key, "9876540002", productId);
        expire(expired);
        try {
            assertThat(listIds(token, "status=EXPIRED&search=" + key))
                    .as("Expired means past its date while still open - the badge's rule")
                    .containsExactly(expired);
            assertThat(listIds(token, "status=DRAFT&search=" + key))
                    .as("Draft means the ones the badge still calls Draft")
                    .containsExactly(live);
            assertThat(listIds(token, "search=" + key))
                    .as("no status filter lists both")
                    .containsExactlyInAnyOrder(live, expired);
        } finally {
            mockMvc.perform(delete("/v1/quotations/" + live).header("Authorization", token));
            mockMvc.perform(delete("/v1/quotations/" + expired).header("Authorization", token));
        }
    }

    @Test
    @DisplayName("CR-083: stats count every matching quotation into the badge's buckets, with rupee totals")
    void statsFollowSearchAndBucketByComputedExpiry() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        String key = "Stats probe " + System.nanoTime();
        long productId = firstProductId(token);
        long live = createDraft(token, key, "9876540003", productId);
        long expired = createDraft(token, key, "9876540004", productId);
        expire(expired);
        try {
            String body = mockMvc.perform(get("/v1/quotations/stats?search=" + key).header("Authorization", token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode data = tree(body).path("data");
            assertThat(data.path("totalCount").asLong()).isEqualTo(2);
            assertThat(data.path("pendingCount").asLong()).as("the live draft").isEqualTo(1);
            assertThat(data.path("approvedCount").asLong()).isZero();
            assertThat(data.path("closedCount").asLong()).as("the expired draft").isEqualTo(1);
            // Two identical quotations: the total is exactly twice either bucket.
            assertThat(data.path("pendingValueDisplay").asText())
                    .isEqualTo(data.path("closedValueDisplay").asText())
                    .isNotEqualTo("0.00");
            assertThat(data.path("totalValueDisplay").asText()).isNotEqualTo(data.path("pendingValueDisplay").asText());

            // A window that excludes today excludes everything created just now.
            mockMvc.perform(get("/v1/quotations/stats?search=" + key + "&toDate=" + LocalDate.now().minusDays(1))
                            .header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalCount").value(0))
                    .andExpect(jsonPath("$.data.totalValueDisplay").value("0.00"));
        } finally {
            mockMvc.perform(delete("/v1/quotations/" + live).header("Authorization", token));
            mockMvc.perform(delete("/v1/quotations/" + expired).header("Authorization", token));
        }
    }

    @Test
    @DisplayName("CR-083: a draft can be deleted; a sent quotation is refused with 422 and stays")
    void deleteIsDraftOnly() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        String key = "Delete probe " + System.nanoTime();
        long productId = firstProductId(token);
        long draft = createDraft(token, key, "9876540005", productId);
        long sent = createDraft(token, key, "9876540006", productId);
        mockMvc.perform(patch("/v1/quotations/" + sent + "/status")
                        .header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"SENT\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/v1/quotations/" + draft).header("Authorization", token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/quotations/" + draft).header("Authorization", token))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/v1/quotations/" + sent).header("Authorization", token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        mockMvc.perform(get("/v1/quotations/" + sent).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"));

        // QUOTATION_VIEW alone must not delete: the accountant can read quotations but never manage them (V1).
        mockMvc.perform(delete("/v1/quotations/" + sent).header("Authorization", bearer("9840223344", "Account@2026")))
                .andExpect(status().isForbidden());
    }
}
