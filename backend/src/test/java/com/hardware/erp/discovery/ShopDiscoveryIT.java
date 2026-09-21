package com.hardware.erp.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.discovery.dto.DiscoveryDtos.DiscoverySettingRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.CreateProductRequest;
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
 * CR-090. The privacy model, proven against real PostgreSQL with real
 * tenants: what an opted-out shop, a distant shop, a shop that withholds
 * a field, and a non-participating searcher each get.
 *
 * Coordinates are Madurai (9.9252, 78.1198) with small offsets: ~0.009°
 * latitude is ~1 km, so "2 km away" and "50 km away" are literal.
 */
class ShopDiscoveryIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private record Shop(String bearer, Long tenantId, String name, String phone) {}

    private static final BigDecimal MADURAI_LAT = new BigDecimal("9.925200");
    private static final BigDecimal MADURAI_LNG = new BigDecimal("78.119800");

    private Shop registerPremiumShop(String label, String phone) throws Exception {
        String mobile = "9" + (100000000 + ThreadLocalRandom.current().nextInt(899999999));
        String email = "owner" + mobile + "@discoverytest.example";
        String name = label + " " + mobile.substring(5);
        String body = mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(json(new TenantRegistrationRequest(
                                name, "Owner", mobile, email, "Disc@2026",
                                SubscriptionTier.MAX, true, "1.0", "1.0", false))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long tenantId = tree(body).path("data").path("tenantId").asLong();
        jdbc.update("UPDATE tenant SET phone = ? WHERE tenant_id = ?", phone, tenantId);
        return new Shop(bearer(mobile, "Disc@2026"), tenantId, name, phone);
    }

    private void optIn(Shop shop, boolean shareName, boolean sharePhone, boolean shareLocation,
                       BigDecimal lat, BigDecimal lng, int radiusKm) throws Exception {
        mockMvc.perform(put("/v1/discovery/settings").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new DiscoverySettingRequest(true, shareName, sharePhone, shareLocation, true,
                                lat, lng, radiusKm))))
                .andExpect(status().isOk());
    }

    private Long insertProduct(Long tenantId, String code, String name, String stock) {
        Long productId = jdbc.queryForObject("""
                INSERT INTO product (tenant_id, product_code, product_name, unit, selling_price_paise,
                                     purchase_price_paise, mrp_paise, gst_rate_percent, status, created_at)
                VALUES (?, ?, ?, 'PCS', 18000, 12000, 20000, 18, 'ACTIVE', now())
                RETURNING product_id""", Long.class, tenantId, code, name);
        jdbc.update("INSERT INTO stock (tenant_id, product_id, quantity_on_hand, created_at) VALUES (?, ?, CAST(? AS DECIMAL), now())",
                tenantId, productId, stock);
        return productId;
    }

    private long createRequest(Shop shop, Long productId, String quantity) throws Exception {
        String body = mockMvc.perform(post("/v1/product-requests").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateProductRequest(productId, new BigDecimal(quantity), null,
                                "Private Customer", "9876500009"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data").path("id").asLong();
    }

    private JsonNode discover(Shop shop, long requestId) throws Exception {
        String body = mockMvc.perform(post("/v1/product-requests/" + requestId + "/discover")
                        .header("Authorization", shop.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data");
    }

    @Test
    @DisplayName("an opted-in shop 2 km away with the product in stock is found; one that never opted in is not")
    void findsOptedInNearbyShopOnly() throws Exception {
        Shop requester = registerPremiumShop("Requester", "9800000001");
        Shop nearby = registerPremiumShop("Nearby Opted In", "9800000002");
        Shop silent = registerPremiumShop("Nearby Never Opted In", "9800000003");

        optIn(requester, true, true, true, MADURAI_LAT, MADURAI_LNG, 5);
        optIn(nearby, true, true, true, MADURAI_LAT.add(new BigDecimal("0.018")), MADURAI_LNG, 5);
        // `silent` sets nothing - default row, everything off.

        Long requested = insertProduct(requester.tenantId(), "TB-4", "Tower Bolt 4 Inch", "0");
        insertProduct(nearby.tenantId(), "TB-4", "Tower Bolt 4 Inch", "25");
        insertProduct(silent.tenantId(), "TB-4", "Tower Bolt 4 Inch", "99");

        long requestId = createRequest(requester, requested, "5");
        JsonNode result = discover(requester, requestId);

        assertThat(result.path("searchUnavailable").asBoolean()).isFalse();
        JsonNode shops = result.path("shops");
        assertThat(shops).hasSize(1);
        JsonNode shop = shops.get(0);
        assertThat(shop.path("shopName").asText()).isEqualTo(nearby.name());
        assertThat(shop.path("phone").asText()).isEqualTo("9800000002");
        assertThat(shop.path("whatsappUrl").asText()).isEqualTo("https://wa.me/919800000002");
        assertThat(shop.path("distanceKm").asDouble()).isBetween(1.5, 2.5);
        assertThat(shop.path("availability").asText()).isEqualTo("AVAILABLE");
        assertThat(shop.path("matchedProductName").asText()).isEqualTo("Tower Bolt 4 Inch");
        // No source id, no quantity, no price - ever.
        assertThat(shop.has("sourceTenantId")).isFalse();
        assertThat(shop.has("quantityOnHand")).isFalse();
        assertThat(shop.has("sellingPricePaise")).isFalse();
    }

    @Test
    @DisplayName("a shop outside the search radius is not found, even though it is opted in and has stock")
    void radiusIsRespected() throws Exception {
        Shop requester = registerPremiumShop("Requester", "9800000011");
        Shop far = registerPremiumShop("Far Away", "9800000012");
        optIn(requester, true, true, true, MADURAI_LAT, MADURAI_LNG, 5);
        // ~50 km north.
        optIn(far, true, true, true, MADURAI_LAT.add(new BigDecimal("0.45")), MADURAI_LNG, 5);

        Long requested = insertProduct(requester.tenantId(), "HG-1", "Hinge 4 Inch Brass", "0");
        insertProduct(far.tenantId(), "HG-1", "Hinge 4 Inch Brass", "30");

        JsonNode result = discover(requester, createRequest(requester, requested, "2"));
        assertThat(result.path("shops")).isEmpty();
    }

    @Test
    @DisplayName("each field is withheld exactly when that shop said so - name, phone and distance are per-shop decisions")
    void fieldsAreWithheldPerShopConsent() throws Exception {
        Shop requester = registerPremiumShop("Requester", "9800000021");
        Shop anonymous = registerPremiumShop("Anonymous Shop", "9800000022");
        optIn(requester, true, true, true, MADURAI_LAT, MADURAI_LNG, 10);
        // Participates and shares availability, but neither name, phone nor location.
        optIn(anonymous, false, false, false, MADURAI_LAT.add(new BigDecimal("0.027")), MADURAI_LNG, 10);

        Long requested = insertProduct(requester.tenantId(), "PL-1", "PVC Pipe 1 Inch", "0");
        insertProduct(anonymous.tenantId(), "PL-1", "PVC Pipe 1 Inch", "3");

        JsonNode result = discover(requester, createRequest(requester, requested, "10"));
        assertThat(result.path("shops")).hasSize(1);
        JsonNode shop = result.path("shops").get(0);
        // Jackson is configured non_null, so a withheld field is absent, not null - either is correct, neither is a leak.
        assertThat(shop.hasNonNull("shopName")).isFalse();
        assertThat(shop.hasNonNull("phone")).isFalse();
        assertThat(shop.hasNonNull("whatsappUrl")).isFalse();
        assertThat(shop.hasNonNull("distanceKm")).isFalse();
        // 3 on hand against 10 requested: some stock, may not cover it.
        assertThat(shop.path("availability").asText()).isEqualTo("LIKELY_AVAILABLE");
        // The response as a whole must not carry the withheld name anywhere.
        assertThat(result.toString()).doesNotContain(anonymous.name()).doesNotContain("9800000022");
    }

    @Test
    @DisplayName("a shop that opts out stops appearing on the very next search")
    void optingOutTakesEffectImmediately() throws Exception {
        Shop requester = registerPremiumShop("Requester", "9800000031");
        Shop leaver = registerPremiumShop("Leaver", "9800000032");
        optIn(requester, true, true, true, MADURAI_LAT, MADURAI_LNG, 5);
        optIn(leaver, true, true, true, MADURAI_LAT.add(new BigDecimal("0.009")), MADURAI_LNG, 5);

        Long requested = insertProduct(requester.tenantId(), "SC-1", "Screw 12mm", "0");
        insertProduct(leaver.tenantId(), "SC-1", "Screw 12mm", "500");
        long requestId = createRequest(requester, requested, "50");

        assertThat(discover(requester, requestId).path("shops")).hasSize(1);

        mockMvc.perform(put("/v1/discovery/settings").header("Authorization", leaver.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new DiscoverySettingRequest(false, true, true, true, true,
                                MADURAI_LAT.add(new BigDecimal("0.009")), MADURAI_LNG, 5))))
                .andExpect(status().isOk())
                // Off means everything off - the sub-flags cannot survive a disable.
                .andExpect(jsonPath("$.data.shareShopName").value(false))
                .andExpect(jsonPath("$.data.sharePhone").value(false));

        assertThat(discover(requester, requestId).path("shops")).isEmpty();
    }

    @Test
    @DisplayName("a shop that has not opted in cannot search - the network is only open to those in it")
    void nonParticipantCannotSearch() throws Exception {
        Shop outsider = registerPremiumShop("Outsider", "9800000041");
        Shop nearby = registerPremiumShop("Nearby", "9800000042");
        optIn(nearby, true, true, true, MADURAI_LAT, MADURAI_LNG, 5);

        Long requested = insertProduct(outsider.tenantId(), "WR-1", "Wire 1.5 sqmm", "0");
        insertProduct(nearby.tenantId(), "WR-1", "Wire 1.5 sqmm", "100");

        JsonNode result = discover(outsider, createRequest(outsider, requested, "10"));
        assertThat(result.path("searchUnavailable").asBoolean()).isTrue();
        assertThat(result.path("searchUnavailableReason").asText()).contains("Product discovery sharing");
        assertThat(result.path("shops")).isEmpty();
        assertThat(result.toString()).doesNotContain(nearby.name());
    }

    @Test
    @DisplayName("enabling discovery without a location is refused - there is nothing to be discovered AT")
    void enablingWithoutLocationIsRefused() throws Exception {
        Shop shop = registerPremiumShop("No Location", "9800000051");
        mockMvc.perform(put("/v1/discovery/settings").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new DiscoverySettingRequest(true, true, true, true, true, null, null, 5))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a successful search leaves the owner a notification naming the product and the count, never a shop or the customer")
    void ownerIsNotified() throws Exception {
        Shop requester = registerPremiumShop("Requester", "9800000061");
        Shop nearby = registerPremiumShop("Nearby", "9800000062");
        optIn(requester, true, true, true, MADURAI_LAT, MADURAI_LNG, 5);
        optIn(nearby, true, true, true, MADURAI_LAT.add(new BigDecimal("0.009")), MADURAI_LNG, 5);

        Long requested = insertProduct(requester.tenantId(), "TP-1", "Tap 15mm", "0");
        insertProduct(nearby.tenantId(), "TP-1", "Tap 15mm", "12");
        discover(requester, createRequest(requester, requested, "2"));

        String body = mockMvc.perform(get("/v1/owner-notifications").param("unreadOnly", "true")
                        .header("Authorization", requester.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode notifications = tree(body).path("data").path("content");
        assertThat(notifications).hasSize(1);
        JsonNode n = notifications.get(0);
        assertThat(n.path("notificationType").asText()).isEqualTo("PRODUCT_DISCOVERY");
        assertThat(n.path("body").asText()).contains("Tap 15mm").contains("1 nearby participating shop");
        assertThat(n.path("body").asText()).doesNotContain(nearby.name()).doesNotContain("Private Customer");

        mockMvc.perform(get("/v1/owner-notifications/unread-count").header("Authorization", requester.bearer()))
                .andExpect(jsonPath("$.data.unread").value(1));
        mockMvc.perform(post("/v1/owner-notifications/" + n.path("id").asLong() + "/read")
                        .header("Authorization", requester.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
        // Another shop cannot read or mark it.
        mockMvc.perform(post("/v1/owner-notifications/" + n.path("id").asLong() + "/read")
                        .header("Authorization", nearby.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a Basic shop is refused the whole feature")
    void basicShopIsRefused() throws Exception {
        String mobile = "9" + (100000000 + ThreadLocalRandom.current().nextInt(899999999));
        mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(json(new TenantRegistrationRequest("Basic Discovery Shop", "Owner", mobile,
                                "owner" + mobile + "@discbasic.example", "Basic@2026",
                                SubscriptionTier.FREE, true, "1.0", "1.0", false))))
                .andExpect(status().isCreated());
        String bearer = bearer(mobile, "Basic@2026");

        mockMvc.perform(put("/v1/discovery/settings").header("Authorization", bearer)
                        .contentType(APPLICATION_JSON)
                        .content(json(new DiscoverySettingRequest(true, true, true, true, true,
                                MADURAI_LAT, MADURAI_LNG, 5))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"));
    }
}
