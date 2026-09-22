package com.hardware.erp.product.substitute;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.CreateProductRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.CreateRelationshipRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SelectAlternativeRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SubstituteSettingRequest;
import com.hardware.erp.product.substitute.entity.RelationshipType;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-089. The whole flow against a real PostgreSQL: out-of-stock product ->
 * request -> scored alternatives -> compare -> owner selects.
 *
 * Every product is created inside a freshly registered shop rather than in
 * the seeded tenant, so this suite adds nothing another IT could trip over
 * and its scores are not affected by the seed catalogue.
 */
class ProductRequestIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private record Shop(String bearer, Long tenantId) {}

    private Shop registerPremiumShop(String password) throws Exception {
        String mobile = "9" + (100000000 + ThreadLocalRandom.current().nextInt(899999999));
        String email = "owner" + mobile + "@substitutetest.example";
        String body = mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(json(new TenantRegistrationRequest(
                                "Substitute Test Shop " + mobile, "Test Owner", mobile, email, password,
                                SubscriptionTier.MAX, true, "1.0", "1.0", false, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long tenantId = tree(body).path("data").path("tenantId").asLong();
        return new Shop(bearer(mobile, password), tenantId);
    }

    /**
     * Products are inserted directly: the attributes CR-089 scores on are
     * what matters here, and going through POST /v1/products would make the
     * fixture three times the size for no extra coverage of this feature.
     */
    private Long insertProduct(Long tenantId, String code, String name, Long categoryId,
                               String productType, String usage, String size, String material,
                               long sellingPricePaise, String stockOnHand) {
        Long productId = jdbc.queryForObject("""
                INSERT INTO product (tenant_id, product_code, product_name, category_id, unit,
                                     selling_price_paise, purchase_price_paise, mrp_paise,
                                     product_type, usage_type, size_label, material,
                                     gst_rate_percent, status, created_at)
                VALUES (?, ?, ?, ?, 'PCS', ?, 0, 0, ?, ?, ?, ?, 18, 'ACTIVE', now())
                RETURNING product_id""",
                Long.class, tenantId, code, name, categoryId, sellingPricePaise,
                productType, usage, size, material);
        jdbc.update("""
                INSERT INTO stock (tenant_id, product_id, quantity_on_hand, created_at)
                VALUES (?, ?, CAST(? AS DECIMAL), now())""", tenantId, productId, stockOnHand);
        return productId;
    }

    private Long insertCategory(Long tenantId, String name) {
        return jdbc.queryForObject("""
                INSERT INTO category (tenant_id, category_code, category_name, status, created_at)
                VALUES (?, ?, ?, 'ACTIVE', now())
                RETURNING category_id""",
                Long.class, tenantId, "CAT-" + Math.abs(name.hashCode() % 100000), name);
    }

    @Test
    @DisplayName("an out-of-stock request returns the in-stock alternatives, best first, with a reason and a match level")
    void suggestsAlternativesForAnOutOfStockProduct() throws Exception {
        Shop shop = registerPremiumShop("Subst@2026");
        Long doorFittings = insertCategory(shop.tenantId(), "Door Fittings");

        Long requested = insertProduct(shop.tenantId(), "TB-4-MS", "Tower Bolt 4 Inch", doorFittings,
                "Tower Bolt", "Door Security", "4 Inch", "MS", 18_000L, "0");
        insertProduct(shop.tenantId(), "TB-4-SS", "SS Tower Bolt 4 Inch", doorFittings,
                "Tower Bolt", "Door Security", "4 Inch", "Stainless Steel", 20_000L, "12");
        insertProduct(shop.tenantId(), "TB-6-MS", "Tower Bolt 6 Inch", doorFittings,
                "Tower Bolt", "Door Security", "6 Inch", "MS", 18_500L, "8");
        // Out of stock itself - must never be offered as an alternative (§8).
        insertProduct(shop.tenantId(), "TB-4-HD", "Heavy Duty Tower Bolt 4 Inch", doorFittings,
                "Tower Bolt", "Door Security", "4 Inch", "MS", 25_000L, "0");

        String body = mockMvc.perform(post("/v1/product-requests").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateProductRequest(requested, new BigDecimal("5"), null,
                                "Ramesh", "9876500001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.requestedProductStock").value(0))
                .andReturn().getResponse().getContentAsString();

        JsonNode suggestions = tree(body).path("data").path("suggestions");
        assertThat(suggestions).hasSizeGreaterThanOrEqualTo(2);
        // Default max_results is 3 (§11's "Top 3").
        assertThat(suggestions.size()).isLessThanOrEqualTo(3);

        JsonNode best = suggestions.get(0);
        assertThat(best.path("product").path("productName").asText()).isEqualTo("SS Tower Bolt 4 Inch");
        assertThat(best.path("score").asInt()).isGreaterThan(suggestions.get(1).path("score").asInt());
        assertThat(best.path("matchLevel").asText()).isIn("EXCELLENT", "HIGH");
        assertThat(best.path("reason").asText()).isNotBlank();
        assertThat(best.path("maximumScore").asInt()).isEqualTo(110);

        // The out-of-stock candidate never appears.
        for (JsonNode suggestion : suggestions) {
            assertThat(suggestion.path("product").path("productName").asText())
                    .isNotEqualTo("Heavy Duty Tower Bolt 4 Inch");
            assertThat(suggestion.path("product").path("inStock").asBoolean()).isTrue();
        }
    }

    @Test
    @DisplayName("a manual mapping outranks the generic similarity score, whatever the attributes say")
    void manualMappingOutranksSimilarity() throws Exception {
        Shop shop = registerPremiumShop("Manual@2026");
        Long category = insertCategory(shop.tenantId(), "Plumbing");

        Long requested = insertProduct(shop.tenantId(), "PL-EL-1", "Elbow 1 Inch", category,
                "Elbow", "Plumbing", "1 Inch", "PVC", 5_000L, "0");
        // Near-identical on attributes - would win on score alone.
        insertProduct(shop.tenantId(), "PL-EL-2", "Elbow 1 Inch Grey", category,
                "Elbow", "Plumbing", "1 Inch", "PVC", 5_200L, "20");
        // Shares only the category, but the owner says it is the replacement.
        Long mapped = insertProduct(shop.tenantId(), "PL-EL-3", "CPVC Elbow 1 Inch", category,
                "Fitting", "Hot Water", "1 Inch CPVC", "CPVC", 9_000L, "15");

        mockMvc.perform(post("/v1/products/" + requested + "/alternative-mappings")
                        .header("Authorization", shop.bearer()).contentType(APPLICATION_JSON)
                        .content(json(new CreateRelationshipRequest(mapped, RelationshipType.REPLACEMENT,
                                "Approved substitute for hot-water lines"))))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/v1/product-requests").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateProductRequest(requested, new BigDecimal("2"), null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode best = tree(body).path("data").path("suggestions").get(0);
        assertThat(best.path("product").path("id").asLong()).isEqualTo(mapped);
        assertThat(best.path("source").asText()).isEqualTo("MANUAL_MAPPING");
        assertThat(best.path("reason").asText()).contains("replacement");
    }

    @Test
    @DisplayName("the owner selecting an alternative resolves the request and records who chose what - and changes no invoice")
    void selectingAnAlternativeResolvesTheRequest() throws Exception {
        Shop shop = registerPremiumShop("Select@2026");
        Long category = insertCategory(shop.tenantId(), "Fasteners");
        Long requested = insertProduct(shop.tenantId(), "SC-10", "Screw 10mm", category,
                "Screw", "Fixing", "10mm", "MS", 1_000L, "0");
        Long alternative = insertProduct(shop.tenantId(), "SC-12", "Screw 12mm", category,
                "Screw", "Fixing", "12mm", "MS", 1_100L, "50");

        String created = mockMvc.perform(post("/v1/product-requests").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateProductRequest(requested, new BigDecimal("10"), null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long requestId = tree(created).path("data").path("id").asLong();

        mockMvc.perform(post("/v1/product-requests/" + requestId + "/select-alternative")
                        .header("Authorization", shop.bearer()).contentType(APPLICATION_JSON)
                        .content(json(new SelectAlternativeRequest(alternative))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.selectedProduct.id").value(alternative))
                .andExpect(jsonPath("$.data.selectedAt").isNotEmpty());

        // §13/§24 - no invoice was created or altered by choosing an alternative.
        Integer invoices = jdbc.queryForObject(
                "SELECT count(*) FROM invoice WHERE tenant_id = ?", Integer.class, shop.tenantId());
        assertThat(invoices).isZero();
    }

    @Test
    @DisplayName("a product that was never suggested cannot be recorded as the selected alternative")
    void cannotSelectAnUnsuggestedProduct() throws Exception {
        Shop shop = registerPremiumShop("Unsug@2026");
        Long category = insertCategory(shop.tenantId(), "Paints");
        Long requested = insertProduct(shop.tenantId(), "PT-1", "Emulsion 1L", category,
                "Paint", "Interior", "1L", "Acrylic", 30_000L, "0");
        // A different category entirely - never in the candidate pool.
        Long unrelated = insertProduct(shop.tenantId(), "XX-1", "Unrelated Hammer", null,
                "Hammer", "Striking", "Standard", "Steel", 40_000L, "10");

        String created = mockMvc.perform(post("/v1/product-requests").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateProductRequest(requested, new BigDecimal("1"), null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long requestId = tree(created).path("data").path("id").asLong();

        mockMvc.perform(post("/v1/product-requests/" + requestId + "/select-alternative")
                        .header("Authorization", shop.bearer()).contentType(APPLICATION_JSON)
                        .content(json(new SelectAlternativeRequest(unrelated))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("raising the minimum score hides weaker alternatives immediately")
    void ownerThresholdFiltersWeakMatches() throws Exception {
        Shop shop = registerPremiumShop("Thresh@2026");
        Long category = insertCategory(shop.tenantId(), "Tools");
        Long requested = insertProduct(shop.tenantId(), "TL-1", "Spanner 10mm", category,
                "Spanner", "Turning", "10mm", "CRV", 20_000L, "0");
        // Shares the category and nothing else: scores 40 (category + stock).
        insertProduct(shop.tenantId(), "TL-2", "Measuring Tape 5m", category,
                "Tape", "Measuring", "5m", "Plastic", 60_000L, "10");

        mockMvc.perform(post("/v1/product-requests").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateProductRequest(requested, new BigDecimal("1"), null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.suggestions.length()").value(1));

        mockMvc.perform(put("/v1/substitute-settings").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new SubstituteSettingRequest(75, true, 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minScoreThreshold").value(75));

        mockMvc.perform(post("/v1/product-requests").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateProductRequest(requested, new BigDecimal("1"), null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.suggestions.length()").value(0));
    }

    @Test
    @DisplayName("the compare view puts the requested product and one alternative side by side, marking what actually matches")
    void compareShowsAttributesSideBySide() throws Exception {
        Shop shop = registerPremiumShop("Compare@2026");
        Long category = insertCategory(shop.tenantId(), "Door Fittings");
        Long requested = insertProduct(shop.tenantId(), "CMP-1", "Tower Bolt 4 Inch", category,
                "Tower Bolt", "Door Security", "4 Inch", "MS", 18_000L, "0");
        Long alternative = insertProduct(shop.tenantId(), "CMP-2", "SS Tower Bolt 4 Inch", category,
                "Tower Bolt", "Door Security", "4 Inch", "Stainless Steel", 22_000L, "9");

        String created = mockMvc.perform(post("/v1/product-requests").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateProductRequest(requested, new BigDecimal("2"), null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long requestId = tree(created).path("data").path("id").asLong();

        String body = mockMvc.perform(get("/v1/product-requests/" + requestId + "/compare")
                        .param("alternativeProductId", String.valueOf(alternative))
                        .header("Authorization", shop.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode rows = tree(body).path("data").path("rows");
        assertThat(rows).isNotEmpty();
        JsonNode sizeRow = findRow(rows, "Size");
        assertThat(sizeRow.path("same").asBoolean()).isTrue();
        JsonNode materialRow = findRow(rows, "Material");
        assertThat(materialRow.path("same").asBoolean()).isFalse();
        assertThat(materialRow.path("requestedValue").asText()).isEqualTo("MS");
        assertThat(materialRow.path("alternativeValue").asText()).isEqualTo("Stainless Steel");
    }

    @Test
    @DisplayName("a Basic shop is refused the whole feature with 403 FEATURE_NOT_AVAILABLE, never a partial answer")
    void basicShopIsRefusedTheFeature() throws Exception {
        String mobile = "9" + (100000000 + ThreadLocalRandom.current().nextInt(899999999));
        String email = "owner" + mobile + "@substitutebasic.example";
        String body = mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(json(new TenantRegistrationRequest(
                                "Basic Substitute Shop " + mobile, "Basic Owner", mobile, email, "Basic@2026",
                                SubscriptionTier.FREE, true, "1.0", "1.0", false, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long tenantId = tree(body).path("data").path("tenantId").asLong();
        String bearer = bearer(mobile, "Basic@2026");

        Long category = insertCategory(tenantId, "Door Fittings");
        Long requested = insertProduct(tenantId, "BS-1", "Tower Bolt 4 Inch", category,
                "Tower Bolt", "Door Security", "4 Inch", "MS", 18_000L, "0");

        mockMvc.perform(post("/v1/product-requests").header("Authorization", bearer)
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateProductRequest(requested, new BigDecimal("1"), null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.errors.requiredPlanCode").value("PREMIUM"));
    }

    @Test
    @DisplayName("one shop's product request is invisible to another shop")
    void productRequestsAreTenantIsolated() throws Exception {
        Shop shopA = registerPremiumShop("IsoA@2026");
        Shop shopB = registerPremiumShop("IsoB@2026");
        Long category = insertCategory(shopA.tenantId(), "Door Fittings");
        Long requested = insertProduct(shopA.tenantId(), "ISO-1", "Tower Bolt 4 Inch", category,
                "Tower Bolt", "Door Security", "4 Inch", "MS", 18_000L, "0");

        String created = mockMvc.perform(post("/v1/product-requests").header("Authorization", shopA.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateProductRequest(requested, new BigDecimal("1"), null,
                                "Private Customer", "9876500002"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long requestId = tree(created).path("data").path("id").asLong();

        String refused = mockMvc.perform(get("/v1/product-requests/" + requestId)
                        .header("Authorization", shopB.bearer()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(refused).doesNotContain("Private Customer").doesNotContain("9876500002");
    }

    private JsonNode findRow(JsonNode rows, String label) {
        for (JsonNode row : rows) {
            if (label.equals(row.path("label").asText())) {
                return row;
            }
        }
        throw new AssertionError("No comparison row labelled " + label);
    }
}
