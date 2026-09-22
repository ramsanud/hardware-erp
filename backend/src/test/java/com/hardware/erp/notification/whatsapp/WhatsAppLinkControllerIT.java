package com.hardware.erp.notification.whatsapp;

import com.hardware.erp.customer.dto.CustomerRequest;
import com.hardware.erp.customer.entity.CustomerStatus;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-080 - the link endpoint through the real security chain.
 *
 * The isolation test is the one that matters: the link is built from the
 * customer's own number, so a cross-tenant read here would hand one shop
 * another shop's customer phone number inside a URL. It must be a 404, and a
 * 404 whose body carries nothing about the record.
 */
class WhatsAppLinkControllerIT extends AbstractIntegrationTest {

    private String owner() throws Exception {
        return bearer(OWNER_MOBILE, OWNER_PASSWORD);
    }

    private long createCustomer(String bearer, String name, String mobile) throws Exception {
        CustomerRequest request = new CustomerRequest(name, mobile, null, null, "1 Test Street", null,
                "Madurai", "33", "625001", 0L, CustomerStatus.ACTIVE, null);
        String body = mockMvc.perform(post("/v1/customers").header("Authorization", bearer)
                        .contentType(APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data").path("id").asLong();
    }

    /** A brand-new tenant through the real public endpoint - unique mobile/email per test, as the other ITs do. */
    private String registerSecondTenantOwner(String mobile, String email) throws Exception {
        TenantRegistrationRequest request = new TenantRegistrationRequest(
                "Other Shop Hardware", "Other Owner", mobile, email,
                "Second@2026", null, true, "1.0", "1.0", false, null);
        mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated());
        return bearer(mobile, "Second@2026");
    }

    @Test
    @DisplayName("the link opens the customer's own number with the greeting typed, and stores nothing")
    void customerLinkForOwnTenant() throws Exception {
        long customerId = createCustomer(owner(), "Ravi Kumar", "9811100777");

        String body = mockMvc.perform(get("/v1/whatsapp/links/customers/" + customerId).header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toMobileNo").value("9811100777"))
                .andReturn().getResponse().getContentAsString();

        String url = tree(body).path("data").path("url").asText();
        assertThat(url).startsWith("https://wa.me/919811100777?text=");
        String text = URLDecoder.decode(URI.create(url).getRawQuery().substring("text=".length()), StandardCharsets.UTF_8);
        // The shop name is whatever V6 seeded the default tenant with - asserted by
        // shape, not by value, so a seed rename cannot break a WhatsApp test.
        assertThat(text).contains("Hello Ravi Kumar").contains("Thank you for choosing ");
        assertThat(text).isEqualTo(tree(body).path("data").path("message").asText());
    }

    @Test
    @DisplayName("another tenant asking for that customer's link gets a 404 that names nothing")
    void customerLinkIsTenantIsolated() throws Exception {
        long customerId = createCustomer(owner(), "Isolated Customer", "9811100778");
        String otherOwner = registerSecondTenantOwner("9900011177", "other-wa@example.in");

        String body = mockMvc.perform(get("/v1/whatsapp/links/customers/" + customerId).header("Authorization", otherOwner))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("9811100778").doesNotContain("Isolated Customer").doesNotContain("wa.me");
    }

    @Test
    @DisplayName("an unauthenticated caller gets 401 - there is no public link generator")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/whatsapp/links/customers/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an unknown invoice is a plain 404, not a 500")
    void unknownInvoiceIs404() throws Exception {
        mockMvc.perform(get("/v1/whatsapp/links/invoices/99999999").header("Authorization", owner()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/whatsapp/links/invoices/99999999/reminder").header("Authorization", owner()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/whatsapp/links/quotations/99999999").header("Authorization", owner()))
                .andExpect(status().isNotFound());
    }
}
