package com.hardware.erp.auth.controller;

import com.hardware.erp.auth.dto.RoleRequest;
import com.hardware.erp.auth.entity.PermissionCode;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoleControllerIT extends AbstractIntegrationTest {

    private String owner() throws Exception {
        return bearer(OWNER_MOBILE, OWNER_PASSWORD);
    }

    @Test
    @DisplayName("the four system roles plus the seeded custom role are listed")
    void listRoles() throws Exception {
        mockMvc.perform(get("/v1/roles").header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.code=='OWNER')].systemRole").value(true))
                .andExpect(jsonPath("$.data[?(@.code=='STOCK_CLERK')].systemRole").value(false));
    }

    @Test
    @DisplayName("STAFF deliberately lacks PRODUCT_VIEW_COST")
    void staffCannotSeeCost() throws Exception {
        String body = mockMvc.perform(get("/v1/roles").header("Authorization", owner()))
                .andReturn().getResponse().getContentAsString();

        var staff = tree(body).path("data").findValues("code").stream()
                .filter(n -> "STAFF".equals(n.asText())).findFirst();
        assertThat(staff).isPresent();

        for (var role : tree(body).path("data")) {
            if ("STAFF".equals(role.path("code").asText())) {
                assertThat(role.path("permissions").toString())
                        .as("counter staff must not see purchase cost or margin")
                        .doesNotContain(PermissionCode.PRODUCT_VIEW_COST);
            }
        }
    }

    @Test
    @DisplayName("permissions are grouped by module for the picker")
    void permissionsGrouped() throws Exception {
        mockMvc.perform(get("/v1/permissions/grouped").header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].moduleCode").exists())
                .andExpect(jsonPath("$.data[0].permissions").isArray());
    }

    @Test
    @DisplayName("a custom role can be created, updated and deleted")
    void customRoleLifecycle() throws Exception {
        String body = mockMvc.perform(post("/v1/roles").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new RoleRequest("PACKER", "Packer", "Packs orders",
                                Set.of(PermissionCode.PRODUCT_VIEW), RoleStatus.ACTIVE))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.systemRole").value(false))
                .andReturn().getResponse().getContentAsString();
        long id = tree(body).path("data").path("id").asLong();

        mockMvc.perform(put("/v1/roles/" + id).header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new RoleRequest("PACKER", "Packer", "Updated",
                                Set.of(PermissionCode.PRODUCT_VIEW,
                                        PermissionCode.INVENTORY_VIEW), RoleStatus.ACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(2));

        mockMvc.perform(delete("/v1/roles/" + id).header("Authorization", owner()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a duplicate role code gives 409")
    void duplicateRoleCode() throws Exception {
        mockMvc.perform(post("/v1/roles").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new RoleRequest("STAFF", "Staff Duplicate", null,
                                Set.of(PermissionCode.PRODUCT_VIEW), RoleStatus.ACTIVE))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("an unknown permission code gives 422 and names the code")
    void unknownPermission() throws Exception {
        mockMvc.perform(post("/v1/roles").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new RoleRequest("BADROLE", "Bad Role", null,
                                Set.of("NOT_A_REAL_PERMISSION"), RoleStatus.ACTIVE))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("NOT_A_REAL_PERMISSION")));
    }

    @Test
    @DisplayName("an empty permission set is rejected by validation")
    void emptyPermissions() throws Exception {
        mockMvc.perform(post("/v1/roles").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new RoleRequest("EMPTYROLE", "Empty", null,
                                Set.of(), RoleStatus.ACTIVE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.permissions").exists());
    }

    @Test
    @DisplayName("a lowercase role code is rejected by the pattern")
    void invalidRoleCodeFormat() throws Exception {
        mockMvc.perform(post("/v1/roles").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new RoleRequest("lowercase", "Lower", null,
                                Set.of(PermissionCode.PRODUCT_VIEW), RoleStatus.ACTIVE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").exists());
    }

    @Test
    @DisplayName("a system role cannot be deleted")
    void systemRoleProtected() throws Exception {
        mockMvc.perform(delete("/v1/roles/4").header("Authorization", owner()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("System roles")));
    }

    @Test
    @DisplayName("the OWNER role cannot lose a permission")
    void ownerRoleKeepsEverything() throws Exception {
        mockMvc.perform(put("/v1/roles/1").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new RoleRequest("OWNER", "Owner", "Reduced",
                                Set.of(PermissionCode.PRODUCT_VIEW), RoleStatus.ACTIVE))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a role still held by users cannot be deleted")
    void roleInUseCannotBeDeleted() throws Exception {
        mockMvc.perform(delete("/v1/roles/1").header("Authorization", owner()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("STAFF cannot read or manage roles")
    void staffForbidden() throws Exception {
        String staff = bearer(STAFF_MOBILE, STAFF_PASSWORD);

        mockMvc.perform(get("/v1/roles").header("Authorization", staff))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/roles").header("Authorization", staff)
                        .contentType(APPLICATION_JSON)
                        .content(json(new RoleRequest("HACK", "Hack", null,
                                Set.of(PermissionCode.USER_MANAGE), RoleStatus.ACTIVE))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("fetching an unknown role gives 404")
    void unknownRole() throws Exception {
        mockMvc.perform(get("/v1/roles/999999").header("Authorization", owner()))
                .andExpect(status().isNotFound());
    }
}
