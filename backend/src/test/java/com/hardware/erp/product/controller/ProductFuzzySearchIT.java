package com.hardware.erp.product.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.product.dto.ProductRequest;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import org.junit.jupiter.api.BeforeEach;
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
 * CR-097 - the pg_trgm fallback behind GET /v1/products?search=, against
 * real PostgreSQL with V64 applied. Nothing here can be proven with a mocked
 * repository: the questions are whether the extension and its partial GIN
 * indexes exist, whether {@code <%} honours a SET LOCAL threshold, whether a
 * native query that bypasses @SQLRestriction still hides deleted rows, and
 * whether tenant_id in a native WHERE clause is enough to keep one shop's
 * catalogue out of another's search box.
 *
 * The expected scores were measured on PostgreSQL 16 before the assertions
 * were written (word_similarity at threshold 0.5): "towr bolt" against
 * "Tower Bolt 6 inch" and "Tower Bolt Brass 4 inch" is 0.615, against the
 * seeded "M8 x 50mm Hex Bolt (box of 100)" exactly 0.5 (included - the
 * operator is >=); "towr bolt 6in" is 0.529 against the 6-inch bolt and
 * 0.471 against the brass one (excluded); "hammr" against the seeded
 * "Stanley Claw Hammer 450g" is 0.667. The assertions are on order and on
 * bounds, never on the third decimal, so a pg_trgm patch release does not
 * turn into a failing build.
 */
class ProductFuzzySearchIT extends AbstractIntegrationTest {

    private static final String TOWER_BOLT_6 = "Tower Bolt 6 inch";
    private static final String TOWER_BOLT_BRASS = "Tower Bolt Brass 4 inch";
    private static final String TOWER_BOLT_DELETED = "Tower Bolt Deleted 8 inch";
    private static final String SEEDED_HEX_BOLT = "M8 x 50mm Hex Bolt (box of 100)";
    private static final String SEEDED_HAMMER = "Stanley Claw Hammer 450g";

    private static boolean catalogueReady;

    private String owner() throws Exception {
        return bearer(OWNER_MOBILE, OWNER_PASSWORD);
    }

    private ProductRequest product(String code, String name) {
        return new ProductRequest(code, name, null, null, null, null, null, "PCS",
                null, "7318", new BigDecimal("18.00"), 4_000L, 6_500L, 8_000L,
                BigDecimal.ZERO, BigDecimal.ZERO, ProductStatus.ACTIVE, null, null);
    }

    /**
     * Three products the seed does not have, created once through the real
     * endpoint (so they carry tenant 1 and go through the same validation as
     * anything a shop enters), one of them soft-deleted straight away.
     */
    @BeforeEach
    void catalogue() throws Exception {
        if (catalogueReady) {
            return;
        }
        create("PRD-960001", TOWER_BOLT_6);
        create("PRD-960002", TOWER_BOLT_BRASS);
        long deletedId = create("PRD-960003", TOWER_BOLT_DELETED);
        mockMvc.perform(delete("/v1/products/" + deletedId).header("Authorization", owner()))
                .andExpect(status().isNoContent());
        catalogueReady = true;
    }

    private long create(String code, String name) throws Exception {
        String body = mockMvc.perform(post("/v1/products").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(product(code, name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data").path("id").asLong();
    }

    private JsonNode search(String bearer, String term) throws Exception {
        String body = mockMvc.perform(get("/v1/products").header("Authorization", bearer)
                        .param("search", term).param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data");
    }

    private static List<String> names(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.path("content").forEach(row -> names.add(row.path("productName").asText()));
        return names;
    }

    private static List<Double> scores(JsonNode page) {
        List<Double> scores = new ArrayList<>();
        // Jackson drops null fields (non_null inclusion), so an ordinary page
        // has no matchScore key at all - missing and null both mean "exact".
        page.path("content").forEach(row -> scores.add(
                row.hasNonNull("matchScore") ? row.get("matchScore").asDouble() : null));
        return scores;
    }

    // ---------------------------------------------------------------------
    // Exact first
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("A substring match is served as before - no matchScore, no look-alikes mixed in")
    void exactMatchIsNeverDilutedByFuzzyMatches() throws Exception {
        JsonNode page = search(owner(), "hammer");

        assertThat(names(page)).contains(SEEDED_HAMMER);
        // Every row is a genuine substring match; the fuzzy path did not run.
        assertThat(scores(page)).containsOnlyNulls();
        assertThat(names(page)).allMatch(n -> n.toLowerCase().contains("hammer"));
    }

    @Test
    @DisplayName("A term the substring search cannot place falls through to the closest matches, scored")
    void typoFallsBackToTrigramMatches() throws Exception {
        JsonNode page = search(owner(), "hammr");

        List<String> names = names(page);
        List<Double> scores = scores(page);
        // Not "first": other suites in this shared container add hammers of
        // their own ("Claw Hammer 500g", "Hammer") that tie on score.
        assertThat(names).contains(SEEDED_HAMMER);
        assertThat(scores).doesNotContainNull();
        assertThat(scores.get(names.indexOf(SEEDED_HAMMER))).isBetween(0.5, 1.0);
        assertThat(names).allMatch(n -> n.toLowerCase().contains("hamm") || n.toLowerCase().contains("hamr"));
    }

    // ---------------------------------------------------------------------
    // Ranking and threshold
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("\"towr bolt\" finds Tower Bolt: closest first, scores descending, every row at or above the threshold")
    void closestMatchRanksFirst() throws Exception {
        JsonNode page = search(owner(), "towr bolt");

        List<String> names = names(page);
        List<Double> scores = scores(page);
        assertThat(names).startsWith(TOWER_BOLT_6, TOWER_BOLT_BRASS);
        // The seeded hex bolt sits at exactly the 0.5 threshold and the
        // operator is >=, so it is admitted - but after both Tower Bolts.
        assertThat(names).contains(SEEDED_HEX_BOLT);
        assertThat(names.indexOf(SEEDED_HEX_BOLT)).isGreaterThan(names.indexOf(TOWER_BOLT_BRASS));

        assertThat(scores).doesNotContainNull();
        assertThat(scores).isSortedAccordingTo((a, b) -> Double.compare(b, a));
        assertThat(scores).allMatch(s -> s >= 0.5 && s <= 1.0);
        assertThat(scores.get(0)).isGreaterThan(scores.get(names.indexOf(SEEDED_HEX_BOLT)));
    }

    @Test
    @DisplayName("More of the term typed narrows the page: below the threshold a near-miss drops out")
    void thresholdExcludesWeakerMatches() throws Exception {
        JsonNode page = search(owner(), "towr bolt 6in");

        assertThat(names(page)).startsWith(TOWER_BOLT_6);
        assertThat(names(page)).doesNotContain(TOWER_BOLT_BRASS, SEEDED_HEX_BOLT);
        assertThat(page.path("totalElements").asLong()).isEqualTo(names(page).size());
    }

    @Test
    @DisplayName("One or two characters never reach pg_trgm - the empty page stands")
    void tooShortForTrigramsStaysEmpty() throws Exception {
        JsonNode page = search(owner(), "zq");

        assertThat(page.path("totalElements").asLong()).isZero();
        assertThat(names(page)).isEmpty();
    }

    // ---------------------------------------------------------------------
    // What must stay hidden
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("BUG-SUP-006 parity: a soft-deleted product is not a close match either")
    void deletedProductIsNotAFuzzyMatch() throws Exception {
        JsonNode page = search(owner(), "towr bolt");

        assertThat(names(page)).doesNotContain(TOWER_BOLT_DELETED);
    }

    @Test
    @DisplayName("CR-016: another tenant sees none of it - not the exact name, not the closest match")
    void otherTenantSeesNothing() throws Exception {
        TenantRegistrationRequest request = new TenantRegistrationRequest(
                "Fuzzy Search Tenant B", "Tenant B Owner", "9700096001", "fuzzy-b@example.in",
                "TenantB@2026", null, true, "1.0", "1.0", false);
        mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated());
        String tenantB = bearer("9700096001", "TenantB@2026");

        // A 100% match on the name, and the typo that finds it for tenant A.
        assertThat(search(tenantB, TOWER_BOLT_6).path("totalElements").asLong()).isZero();
        assertThat(search(tenantB, "towr bolt").path("totalElements").asLong()).isZero();

        // The same two calls still answer for the tenant that owns the rows.
        assertThat(names(search(owner(), TOWER_BOLT_6))).contains(TOWER_BOLT_6);
        assertThat(names(search(owner(), "towr bolt"))).contains(TOWER_BOLT_6);
    }
}
