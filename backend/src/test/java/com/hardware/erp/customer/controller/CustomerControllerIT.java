package com.hardware.erp.customer.controller;

import com.hardware.erp.auth.dto.CreateUserRequest;
import com.hardware.erp.auth.dto.RoleRequest;
import com.hardware.erp.auth.entity.PermissionCode;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.customer.dto.CustomerRequest;
import com.hardware.erp.customer.entity.CustomerStatus;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Module 5's first controller integration test, added with CR-058 and scoped
 * to it: reactivation, against real PostgreSQL.
 *
 * Customer deliberately has no deleted_at and no {@code @SQLRestriction}, so
 * there is nothing here to "restore" and no deleted-records endpoint - an
 * INACTIVE customer was never hidden. What these assert is the plain status
 * round trip and that it stays inside the caller's own tenant and permission.
 */
class CustomerControllerIT extends AbstractIntegrationTest {

    private String owner() throws Exception {
        return bearer(OWNER_MOBILE, OWNER_PASSWORD);
    }

    private CustomerRequest customer(String name, String mobile) {
        return new CustomerRequest(name, mobile, null, null, "1 Test Street", null,
                "Madurai", "33", "625001", 0L, CustomerStatus.ACTIVE, null);
    }

    @Test
    @DisplayName("CR-058: deactivate then reactivate is a clean round trip through the INACTIVE filter")
    void deactivateThenReactivate() throws Exception {
        String created = mockMvc.perform(post("/v1/customers").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(customer("Round Trip Customer", "9811100250"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = tree(created).path("data").path("id").asLong();

        mockMvc.perform(delete("/v1/customers/" + id).header("Authorization", owner()))
                .andExpect(status().isNoContent());

        // Unlike Supplier/Product/User, the row is never hidden - it simply
        // moves to the INACTIVE filter, which is why Customer needs no
        // deleted-records endpoint at all.
        mockMvc.perform(get("/v1/customers/" + id).header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        String inactive = mockMvc.perform(get("/v1/customers").header("Authorization", owner())
                        .param("search", "9811100250").param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(tree(inactive).path("data").path("totalElements").asInt()).isEqualTo(1);

        mockMvc.perform(post("/v1/customers/" + id + "/activate").header("Authorization", owner()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/customers/" + id).header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value((int) id))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                // Reactivation must touch nothing but the status.
                .andExpect(jsonPath("$.data.customerName").value("Round Trip Customer"))
                .andExpect(jsonPath("$.data.mobileNo").value("9811100250"))
                .andExpect(jsonPath("$.data.city").value("Madurai"));

        String active = mockMvc.perform(get("/v1/customers").header("Authorization", owner())
                        .param("search", "9811100250").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(tree(active).path("data").path("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("CR-058: activating an already-active customer is harmless and idempotent")
    void activateAlreadyActiveIsIdempotent() throws Exception {
        String created = mockMvc.perform(post("/v1/customers").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(customer("Already Active Customer", "9811100251"))))
                .andReturn().getResponse().getContentAsString();
        long id = tree(created).path("data").path("id").asLong();

        mockMvc.perform(post("/v1/customers/" + id + "/activate").header("Authorization", owner()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/customers/" + id).header("Authorization", owner()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("CR-058: activating an unknown customer gives 404")
    void activateUnknownIsNotFound() throws Exception {
        mockMvc.perform(post("/v1/customers/999999/activate").header("Authorization", owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("CR-058: CUSTOMER_VIEW alone cannot reactivate - it needs CUSTOMER_MANAGE")
    void activateRequiresManage() throws Exception {
        // This deliberately builds its own role rather than borrowing a seeded
        // one. NO seeded role can express "view but not manage" for customers:
        // V1__auth_schema.sql grants CUSTOMER_MANAGE to MANAGER, ACCOUNTANT
        // and STAFF alike, and OWNER holds every permission - so an earlier
        // version of this test signed in as ACCOUNTANT, expected 403 and got
        // 204, because ACCOUNTANT is genuinely allowed to reactivate a
        // customer. The 204 was correct and the assertion was wrong.
        //
        // Changing the seed to make the old assertion pass would have been the
        // wrong fix: whether an accountant may reactivate a customer is a
        // product decision, not a detail to bend for a test. So the boundary
        // being asserted - @PreAuthorize(CUSTOMER_MANAGE) on activate - is
        // exercised with a purpose-built role that holds CUSTOMER_VIEW only.
        String owner = owner();

        String roleBody = mockMvc.perform(post("/v1/roles").header("Authorization", owner)
                        .contentType(APPLICATION_JSON)
                        .content(json(new RoleRequest("CUSTOMER_READER", "Customer Reader",
                                "Read-only customer access, for this test",
                                Set.of(PermissionCode.CUSTOMER_VIEW), RoleStatus.ACTIVE))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long roleId = tree(roleBody).path("data").path("id").asLong();

        // mustChangePassword=false so this account can be used straight away -
        // same approach as UserControllerIT.deactivatedUserCannotLogin.
        mockMvc.perform(post("/v1/users").header("Authorization", owner)
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateUserRequest("Read Only Clerk", "9811100252",
                                "readonly@sarahardware.in", "EMP252", roleId,
                                "Welcome@2026", false))))
                .andExpect(status().isCreated());

        String readOnly = bearer("9811100252", "Welcome@2026");

        // Proves the token is genuinely usable and the role really does carry
        // CUSTOMER_VIEW - otherwise the 403 below would prove nothing, since a
        // broken token would produce one too.
        mockMvc.perform(get("/v1/customers").header("Authorization", readOnly))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/customers/1/activate").header("Authorization", readOnly))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("CR-058: an unauthenticated caller cannot reactivate a customer")
    void activateRejectsAnonymous() throws Exception {
        mockMvc.perform(post("/v1/customers/1/activate"))
                .andExpect(status().isUnauthorized());
    }
}
