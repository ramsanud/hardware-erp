package com.hardware.erp.subscription;

import com.hardware.erp.ai.AiChatRequest;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-088 §25. Basic/Pro -> 403 on a Premium-only API, Premium -> success,
 * plus the plan catalogue and per-tenant subscription endpoints. Each shop
 * is registered fresh on an explicit tier through the real public
 * registration endpoint (bypassing the trial policy - spec's §25 wants a
 * shop pinned on a plan, not mid-trial) rather than mutating the seeded
 * tenant, so this suite cannot disturb any other IT reading tenant 1.
 */
class SubscriptionControllerIT extends AbstractIntegrationTest {

    private String registerShopOnTier(SubscriptionTier tier, String password) throws Exception {
        String mobile = "9" + (100000000 + ThreadLocalRandom.current().nextInt(899999999));
        String email = "owner" + mobile + "@subtest.example";
        TenantRegistrationRequest request = new TenantRegistrationRequest(
                tier.name() + " Test Shop", "Test Owner", mobile, email, password, tier,
                true, "1.0", "1.0", false, null);
        mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated());
        return bearer(mobile, password);
    }

    @Test
    @DisplayName("GET /v1/subscriptions/plans lists Basic/Pro/Premium with real prices and feature keys, no auth required")
    void plansIsPublicAndComplete() throws Exception {
        String body = mockMvc.perform(get("/v1/subscriptions/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        var plans = tree(body).path("data");
        assertThat(plans.findValuesAsText("planCode")).containsExactlyInAnyOrder("BASIC", "PRO", "PREMIUM");
        var premium = java.util.stream.StreamSupport.stream(plans.spliterator(), false)
                .filter(p -> p.path("planCode").asText().equals("PREMIUM")).findFirst().orElseThrow();
        assertThat(premium.path("pricePaise").asLong()).isEqualTo(99_900L);
        assertThat(premium.path("featureKeys").isArray()).isTrue();
        assertThat(premium.path("featureKeys").size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("a Basic shop calling the Premium-only AI endpoint gets 403 FEATURE_NOT_AVAILABLE naming Premium")
    void basicShopDeniedPremiumApi() throws Exception {
        String bearer = registerShopOnTier(SubscriptionTier.FREE, "BasicTest@2026");

        mockMvc.perform(post("/v1/ai/chat").header("Authorization", bearer)
                        .contentType(APPLICATION_JSON).content(json(new AiChatRequest("hello", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.errors.currentPlanCode").value("BASIC"))
                .andExpect(jsonPath("$.errors.requiredPlanCode").value("PREMIUM"));
    }

    @Test
    @DisplayName("a Pro shop calling the Premium-only AI endpoint also gets 403")
    void proShopDeniedPremiumApi() throws Exception {
        String bearer = registerShopOnTier(SubscriptionTier.PRO, "ProTest@2026");

        mockMvc.perform(post("/v1/ai/chat").header("Authorization", bearer)
                        .contentType(APPLICATION_JSON).content(json(new AiChatRequest("hello", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.errors.currentPlanCode").value("PRO"));
    }

    @Test
    @DisplayName("a Premium shop passes the AI feature gate (an unconfigured provider still answers 200, past the gate)")
    void premiumShopPassesGate() throws Exception {
        String bearer = registerShopOnTier(SubscriptionTier.MAX, "MaxTest@2026");

        mockMvc.perform(post("/v1/ai/chat").header("Authorization", bearer)
                        .contentType(APPLICATION_JSON).content(json(new AiChatRequest("hello", null))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/features/AI_FEATURES/access reports allowed=false and the required plan for a Basic shop")
    void featureAccessEndpointReportsRequiredPlan() throws Exception {
        String bearer = registerShopOnTier(SubscriptionTier.FREE, "AccessTest@2026");

        mockMvc.perform(get("/v1/features/AI_FEATURES/access").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(false))
                .andExpect(jsonPath("$.data.requiredPlanCode").value("PREMIUM"));
    }

    @Test
    @DisplayName("GET /v1/subscriptions/current is isolated per tenant - two shops never see each other's plan or usage")
    void currentSubscriptionIsTenantIsolated() throws Exception {
        String basicBearer = registerShopOnTier(SubscriptionTier.FREE, "IsoBasic@2026");
        String premiumBearer = registerShopOnTier(SubscriptionTier.MAX, "IsoPremium@2026");

        mockMvc.perform(get("/v1/subscriptions/current").header("Authorization", basicBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCode").value("BASIC"));

        mockMvc.perform(get("/v1/subscriptions/current").header("Authorization", premiumBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCode").value("PREMIUM"));
    }

    @Test
    @DisplayName("upgrading self-service (no payment gateway configured in tests) moves the plan and is reflected immediately")
    void selfServiceUpgradeChangesEffectivePlan() throws Exception {
        String bearer = registerShopOnTier(SubscriptionTier.FREE, "UpgradeTest@2026");

        mockMvc.perform(post("/v1/ai/chat").header("Authorization", bearer)
                        .contentType(APPLICATION_JSON).content(json(new AiChatRequest("hello", null))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/v1/subscriptions/upgrade").header("Authorization", bearer)
                        .contentType(APPLICATION_JSON).content(json(new com.hardware.erp.subscription.dto.ChangePlanRequest("PREMIUM", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCode").value("PREMIUM"));

        mockMvc.perform(post("/v1/ai/chat").header("Authorization", bearer)
                        .contentType(APPLICATION_JSON).content(json(new AiChatRequest("hello", null))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an unauthenticated caller gets 401 on /v1/subscriptions/current, never a plan leak")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/subscriptions/current"))
                .andExpect(status().isUnauthorized());
    }
}
