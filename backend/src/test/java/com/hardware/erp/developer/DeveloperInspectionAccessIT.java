package com.hardware.erp.developer;

import com.hardware.erp.auth.entity.Permission;
import com.hardware.erp.auth.entity.PermissionCode;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.repository.PermissionRepository;
import com.hardware.erp.auth.repository.RoleRepository;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The person half of the CR-045 gate, against a real database.
 *
 * The test profile has app.developer-inspection.enabled=true, so the
 * environment permits inspection here. Everything below therefore isolates
 * the permission: the seeded OWNER holds every ERP permission there is and
 * must still be refused.
 */
class DeveloperInspectionAccessIT extends AbstractIntegrationTest {

    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;

    @Test
    @DisplayName("V30 adds DEVELOPER_INSPECT to the catalogue")
    void permissionExists() {
        assertThat(permissionRepository.findAll())
                .extracting(Permission::getCode)
                .contains(PermissionCode.DEVELOPER_INSPECT);
    }

    /**
     * The single most important assertion in this class. Every other module
     * migration ends by granting OWNER its new codes; V30 deliberately does
     * not, because administering a shop is not the same job as debugging the
     * software. If this ever goes green-to-red, "admin = developer" has crept
     * back in.
     */
    @Test
    @DisplayName("no seeded role holds DEVELOPER_INSPECT - OWNER included")
    void noDefaultRoleHoldsIt() {
        for (Role role : roleRepository.findAll()) {
            assertThat(role.getPermissions())
                    .as("role %s must not hold developer diagnostics by default", role.getCode())
                    .extracting(Permission::getCode)
                    .doesNotContain(PermissionCode.DEVELOPER_INSPECT);
        }
    }

    @Test
    @DisplayName("the owner is refused developer diagnostics despite holding every ERP permission")
    void ownerCannotReadRuntimeDiagnostics() throws Exception {
        mockMvc.perform(get("/v1/dev/inspection/runtime")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OWNER_MOBILE, OWNER_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the owner is refused the request echo too")
    void ownerCannotReadRequestEcho() throws Exception {
        mockMvc.perform(get("/v1/dev/inspection/request-echo")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OWNER_MOBILE, OWNER_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("staff are refused as well")
    void staffCannotReadRuntimeDiagnostics() throws Exception {
        mockMvc.perform(get("/v1/dev/inspection/runtime")
                        .header(HttpHeaders.AUTHORIZATION, bearer(STAFF_MOBILE, STAFF_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    /**
     * Status is readable by any signed-in user so a developer can tell which
     * gate closed. It must still report the honest answer - not availability.
     */
    @Test
    @DisplayName("status reports environment-yes, permission-no for the owner")
    void statusSeparatesTheTwoGates() throws Exception {
        mockMvc.perform(get("/v1/dev/inspection/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OWNER_MOBILE, OWNER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.environmentAllows").value(true))
                .andExpect(jsonPath("$.data.permissionHeld").value(false));
    }

    @Test
    @DisplayName("an anonymous caller reaches nothing under /v1/dev")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/v1/dev/inspection/status"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/dev/inspection/runtime"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Actuator beyond /actuator/health is developer tooling: env and
     * configprops in particular would print the datasource password and the
     * JWT signing key.
     */
    @Test
    @DisplayName("actuator beyond health is closed to the owner")
    void actuatorBeyondHealthIsClosed() throws Exception {
        mockMvc.perform(get("/actuator/env")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OWNER_MOBILE, OWNER_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the liveness probe stays public")
    void healthRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
