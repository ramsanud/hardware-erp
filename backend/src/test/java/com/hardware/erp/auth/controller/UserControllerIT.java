package com.hardware.erp.auth.controller;

import com.hardware.erp.auth.dto.*;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerIT extends AbstractIntegrationTest {

    private static final long STAFF_ROLE_ID = 4L;

    private String owner() throws Exception {
        return bearer(OWNER_MOBILE, OWNER_PASSWORD);
    }

    private CreateUserRequest newUser(String mobile, String email, String code) {
        return new CreateUserRequest("Test Employee", mobile, email, code,
                STAFF_ROLE_ID, "Welcome@2026", true);
    }

    // ---------------- create ----------------

    @Test
    @DisplayName("owner creates a user and gets 201")
    void createValid() throws Exception {
        mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(newUser("9811100001", "t1@sarahardware.in", "EMP101"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Test Employee"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("a duplicate mobile number gives 409")
    void duplicateMobile() throws Exception {
        mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(newUser(OWNER_MOBILE, "t2@sarahardware.in", "EMP102"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("a duplicate email gives 409")
    void duplicateEmail() throws Exception {
        mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(newUser("9811100003", OWNER_EMAIL, "EMP103"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("an email differing only by case is still a duplicate")
    void duplicateEmailDiffersOnlyByCase() throws Exception {
        // Regression for BUG-AUTH-009. Under MySQL the ai_ci collation blocked
        // this for free; PostgreSQL needs the functional unique index on
        // lower(email). Without it this second insert would succeed and
        // findByIdentifier would later match two rows.
        mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(newUser("9811100020", "Case.Test@sarahardware.in", "EMP120"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(newUser("9811100021", "case.test@SARAHARDWARE.in", "EMP121"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("a missing required field gives a field-level 400")
    void missingRequiredField() throws Exception {
        mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateUserRequest("", "9811100004", null, null,
                                STAFF_ROLE_ID, "Welcome@2026", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").exists());
    }

    @Test
    @DisplayName("an invalid mobile format is rejected")
    void invalidMobileFormat() throws Exception {
        mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(newUser("12345", "t5@sarahardware.in", "EMP105"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.mobileNo").exists());
    }

    @Test
    @DisplayName("a weak password is rejected by the backend, not just the UI")
    void weakPassword() throws Exception {
        mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateUserRequest("Weak", "9811100006",
                                "t6@sarahardware.in", "EMP106", STAFF_ROLE_ID,
                                "password", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("an invalid email format is rejected")
    void invalidEmail() throws Exception {
        mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(newUser("9811100007", "not-an-email", "EMP107"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("an unknown role gives 404")
    void unknownRole() throws Exception {
        mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateUserRequest("X", "9811100008",
                                "t8@sarahardware.in", "EMP108", 9999L, "Welcome@2026", false))))
                .andExpect(status().isNotFound());
    }

    // ---------------- read / search / paging ----------------

    @Test
    @DisplayName("the seeded users are listed and soft-deleted rows are excluded")
    void listExcludesDeleted() throws Exception {
        String body = mockMvc.perform(get("/v1/users").header("Authorization", owner())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(DELETED_STAFF_MOBILE);
    }

    @Test
    @DisplayName("search matches on name, mobile and email")
    void search() throws Exception {
        mockMvc.perform(get("/v1/users").header("Authorization", owner())
                        .param("search", "Karthik"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].mobileNo").value(STAFF_MOBILE));
    }

    @Test
    @DisplayName("a search with no matches returns an empty page, not an error")
    void emptyResult() throws Exception {
        mockMvc.perform(get("/v1/users").header("Authorization", owner())
                        .param("search", "zzzznotarealname"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("filtering by status works")
    void filterByStatus() throws Exception {
        mockMvc.perform(get("/v1/users").header("Authorization", owner())
                        .param("status", "SUSPENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].mobileNo").value(SUSPENDED_STAFF_MOBILE));
    }

    @Test
    @DisplayName("pagination returns stable page metadata")
    void pagination() throws Exception {
        mockMvc.perform(get("/v1/users").header("Authorization", owner())
                        .param("page", "0").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(3))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.totalPages").isNumber());
    }

    @Test
    @DisplayName("page size is clamped to 100 so a huge request cannot exhaust memory")
    void pageSizeClamped() throws Exception {
        mockMvc.perform(get("/v1/users").header("Authorization", owner())
                        .param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    @DisplayName("an injection attempt in sortBy falls back to the default")
    void sortWhitelist() throws Exception {
        mockMvc.perform(get("/v1/users").header("Authorization", owner())
                        .param("sortBy", "password_hash; DROP TABLE app_user"))
                .andExpect(status().isOk());

        // Prove the table survived.
        mockMvc.perform(get("/v1/users").header("Authorization", owner()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("fetching an unknown user gives 404")
    void getUnknown() throws Exception {
        mockMvc.perform(get("/v1/users/999999").header("Authorization", owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---------------- update ----------------

    @Test
    @DisplayName("a valid update returns the persisted values")
    void updateValid() throws Exception {
        String body = mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(newUser("9811100010", "t10@sarahardware.in", "EMP110"))))
                .andReturn().getResponse().getContentAsString();
        long id = tree(body).path("data").path("id").asLong();

        mockMvc.perform(put("/v1/users/" + id).header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new UpdateUserRequest("Renamed Employee", "9811100010",
                                "t10@sarahardware.in", "EMP110", STAFF_ROLE_ID,
                                UserStatus.ACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Renamed Employee"));
    }

    @Test
    @DisplayName("updating to a mobile another user already holds gives 409")
    void updateDuplicate() throws Exception {
        String body = mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(newUser("9811100011", "t11@sarahardware.in", "EMP111"))))
                .andReturn().getResponse().getContentAsString();
        long id = tree(body).path("data").path("id").asLong();

        mockMvc.perform(put("/v1/users/" + id).header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new UpdateUserRequest("X", OWNER_MOBILE,
                                "t11@sarahardware.in", "EMP111", STAFF_ROLE_ID,
                                UserStatus.ACTIVE))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("STAFF cannot update a user")
    void unauthorizedUpdate() throws Exception {
        mockMvc.perform(put("/v1/users/5")
                        .header("Authorization", bearer(STAFF_MOBILE, STAFF_PASSWORD))
                        .contentType(APPLICATION_JSON)
                        .content(json(new UpdateUserRequest("Hacked", "9843012345",
                                "karthik@sarahardware.in", "EMP005", 1L, UserStatus.ACTIVE))))
                .andExpect(status().isForbidden());
    }

    // ---------------- delete ----------------

    @Test
    @DisplayName("delete returns 204 and the user disappears from the active list")
    void deleteRemovesFromList() throws Exception {
        String body = mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(newUser("9811100012", "t12@sarahardware.in", "EMP112"))))
                .andReturn().getResponse().getContentAsString();
        long id = tree(body).path("data").path("id").asLong();

        mockMvc.perform(delete("/v1/users/" + id).header("Authorization", owner()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/users/" + id).header("Authorization", owner()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deleting an already-deleted user gives 404")
    void deleteTwice() throws Exception {
        String body = mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(newUser("9811100013", "t13@sarahardware.in", "EMP113"))))
                .andReturn().getResponse().getContentAsString();
        long id = tree(body).path("data").path("id").asLong();

        mockMvc.perform(delete("/v1/users/" + id).header("Authorization", owner()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/v1/users/" + id).header("Authorization", owner()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("STAFF cannot delete a user")
    void unauthorizedDelete() throws Exception {
        mockMvc.perform(delete("/v1/users/5")
                        .header("Authorization", bearer(STAFF_MOBILE, STAFF_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a deactivated user can no longer sign in")
    void deactivatedUserCannotLogin() throws Exception {
        String body = mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateUserRequest("Soon Gone", "9811100014",
                                "t14@sarahardware.in", "EMP114", STAFF_ROLE_ID,
                                "Welcome@2026", false))))
                .andReturn().getResponse().getContentAsString();
        long id = tree(body).path("data").path("id").asLong();

        mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest("9811100014", "Welcome@2026"))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/v1/users/" + id).header("Authorization", owner()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest("9811100014", "Welcome@2026"))))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- last owner ----------------

    @Test
    @DisplayName("with two owners seeded, one can be deactivated")
    void secondOwnerCanBeDeactivated() throws Exception {
        String body = mockMvc.perform(get("/v1/users").header("Authorization", owner())
                        .param("search", SECOND_OWNER_MOBILE))
                .andReturn().getResponse().getContentAsString();
        long id = tree(body).path("data").path("content").get(0).path("id").asLong();

        mockMvc.perform(put("/v1/users/" + id).header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new UpdateUserRequest("Lakshmi Saravanan",
                                SECOND_OWNER_MOBILE, "lakshmi@sarahardware.in", "EMP002",
                                1L, UserStatus.INACTIVE))))
                .andExpect(status().isOk());

        // Restore for other tests.
        mockMvc.perform(put("/v1/users/" + id).header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new UpdateUserRequest("Lakshmi Saravanan",
                                SECOND_OWNER_MOBILE, "lakshmi@sarahardware.in", "EMP002",
                                1L, UserStatus.ACTIVE))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an admin password reset forces a change at next sign-in")
    void adminResetForcesChange() throws Exception {
        String body = mockMvc.perform(post("/v1/users").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateUserRequest("Reset Me", "9811100015",
                                "t15@sarahardware.in", "EMP115", STAFF_ROLE_ID,
                                "Welcome@2026", false))))
                .andReturn().getResponse().getContentAsString();
        long id = tree(body).path("data").path("id").asLong();

        mockMvc.perform(post("/v1/users/" + id + "/reset-password")
                        .header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new ResetUserPasswordRequest("Temp@2026"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest("9811100015", "Temp@2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }
}
