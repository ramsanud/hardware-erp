package com.hardware.erp.supplier.controller;

import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.supplier.dto.SupplierContactRequest;
import com.hardware.erp.supplier.dto.SupplierRequest;
import com.hardware.erp.supplier.entity.SupplierStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Module 2 end to end, against real PostgreSQL with the V901 seed data. */
class SupplierControllerIT extends AbstractIntegrationTest {

    private String owner() throws Exception {
        return bearer(OWNER_MOBILE, OWNER_PASSWORD);
    }

    private SupplierRequest supplier(String code, String name, String mobile,
                                     String gst, String state) {
        return new SupplierRequest(code, name, "Test Contact", mobile, null,
                "test@example.in", gst, null, "1 Test Street", null, "Madurai",
                state, "625001", 30, 10_000_000L, null, null, null, null,
                SupplierStatus.ACTIVE, "Created by an integration test");
    }

    @Test
    @DisplayName("the seeded suppliers are listed and the soft-deleted one is excluded")
    void listExcludesDeleted() throws Exception {
        String body = mockMvc.perform(get("/v1/suppliers").header("Authorization", owner())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Sri Balaji Hardware Agencies");
        assertThat(body).doesNotContain("Old Ganesh Hardware");
    }

    @Test
    @DisplayName("one search box matches name, code, mobile and city")
    void searchAcrossFields() throws Exception {
        mockMvc.perform(get("/v1/suppliers").header("Authorization", owner())
                        .param("search", "balaji"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].supplierCode").value("SUP-0001"));

        mockMvc.perform(get("/v1/suppliers").header("Authorization", owner())
                        .param("search", "9843122334"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].supplierName")
                        .value("Kumaran Steel & Fittings"));
    }

    @Test
    @DisplayName("filtering by status and city works")
    void filters() throws Exception {
        mockMvc.perform(get("/v1/suppliers").header("Authorization", owner())
                        .param("status", "BLOCKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].supplierName").value("Anand Fasteners"));

        mockMvc.perform(get("/v1/suppliers").header("Authorization", owner())
                        .param("city", "Coimbatore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("page size is clamped and an injection attempt in sortBy is ignored")
    void paginationSafety() throws Exception {
        mockMvc.perform(get("/v1/suppliers").header("Authorization", owner())
                        .param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));

        mockMvc.perform(get("/v1/suppliers").header("Authorization", owner())
                        .param("sortBy", "supplier_name; DROP TABLE supplier"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/suppliers").header("Authorization", owner()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("create returns 201 and generates the next code when none is given")
    void createGeneratesCode() throws Exception {
        String body = mockMvc.perform(post("/v1/suppliers").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(supplier("", "Integration Test Traders",
                                "9811100201", null, "33"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.supplierCode").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(tree(body).path("data").path("supplierCode").asText()).startsWith("SUP-");
    }

    @Test
    @DisplayName("a duplicate supplier name is rejected regardless of case")
    void duplicateNameDiffersOnlyByCase() throws Exception {
        mockMvc.perform(post("/v1/suppliers").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(supplier("SUP-9001", "SRI BALAJI HARDWARE AGENCIES",
                                "9811100202", null, "33"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("a GST number whose state disagrees with the address gives 422")
    void gstStateMismatch() throws Exception {
        mockMvc.perform(post("/v1/suppliers").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(supplier("SUP-9002", "Mismatch Traders",
                                "9811100203", "29AABCS1429B1ZP", "33"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("state code")));
    }

    @Test
    @DisplayName("an invalid GSTIN is rejected by validation with a field error")
    void invalidGstFormat() throws Exception {
        mockMvc.perform(post("/v1/suppliers").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(supplier("SUP-9003", "Bad GST Traders",
                                "9811100204", "NOTAGSTIN", "33"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.gstNo").exists());
    }

    @Test
    @DisplayName("an invalid mobile number is rejected")
    void invalidMobile() throws Exception {
        mockMvc.perform(post("/v1/suppliers").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(supplier("SUP-9004", "Bad Mobile Traders",
                                "12345", null, "33"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.mobileNo").exists());
    }

    @Test
    @DisplayName("a negative credit limit is rejected")
    void negativeCreditLimit() throws Exception {
        SupplierRequest bad = new SupplierRequest("SUP-9005", "Negative Traders", null,
                "9811100205", null, null, null, null, null, null, "Madurai", "33",
                null, 30, -1L, null, null, null, null, SupplierStatus.ACTIVE, null);

        mockMvc.perform(post("/v1/suppliers").header("Authorization", owner())
                        .contentType(APPLICATION_JSON).content(json(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.creditLimitPaise").exists());
    }

    @Test
    @DisplayName("the bank account number is masked in responses")
    void bankAccountMasked() throws Exception {
        String body = mockMvc.perform(get("/v1/suppliers/1").header("Authorization", owner()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The seed stores 50100234567890; only the last four digits may return.
        assertThat(body).doesNotContain("50100234567890");
        assertThat(body).contains("7890");
    }

    @Test
    @DisplayName("update, then soft delete, then the supplier leaves the list")
    void lifecycle() throws Exception {
        String created = mockMvc.perform(post("/v1/suppliers").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(supplier("SUP-9010", "Lifecycle Traders",
                                "9811100210", null, "33"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = tree(created).path("data").path("id").asLong();

        mockMvc.perform(put("/v1/suppliers/" + id).header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(supplier("SUP-9010", "Lifecycle Traders Renamed",
                                "9811100210", null, "33"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplierName").value("Lifecycle Traders Renamed"));

        mockMvc.perform(delete("/v1/suppliers/" + id).header("Authorization", owner()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/suppliers/" + id).header("Authorization", owner()))
                .andExpect(status().isNotFound());
    }

    // ---------------- CR-058: deleted records and restore ----------------
    //
    // These are the tests that actually prove the escape hatch works against
    // real PostgreSQL: that a native query genuinely bypasses Supplier's
    // @SQLRestriction (a mocked repository could never show that), and that
    // the restriction is still doing its job everywhere else afterwards.

    @Test
    @DisplayName("CR-058: the deleted list shows the soft-deleted seed row that every other endpoint hides")
    void deletedListSeesWhatSqlRestrictionHides() throws Exception {
        String deleted = mockMvc.perform(get("/v1/suppliers/deleted")
                        .header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        // V901 soft-deletes exactly this row; the ordinary list test above
        // asserts the same name is absent there.
        assertThat(deleted).contains("Old Ganesh Hardware");
        assertThat(deleted).contains("deletedAt");
        // The reduced projection must not ship credit limits or GST numbers.
        assertThat(deleted).doesNotContain("creditLimitDisplay");
    }

    @Test
    @DisplayName("CR-058: delete then restore returns the same supplier row, id and history intact")
    void restoreLifecycle() throws Exception {
        String created = mockMvc.perform(post("/v1/suppliers").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(supplier("SUP-9030", "Restore Me Traders",
                                "9811100240", null, "33"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = tree(created).path("data").path("id").asLong();

        // A contact, so the restore can be shown not to have orphaned children.
        mockMvc.perform(post("/v1/suppliers/" + id + "/contacts").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new SupplierContactRequest(
                                "Restore Contact", "Owner", "9811100241", null, true))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/v1/suppliers/" + id).header("Authorization", owner()))
                .andExpect(status().isNoContent());

        // Hidden from GET-by-id and from search - @SQLRestriction still holds.
        mockMvc.perform(get("/v1/suppliers/" + id).header("Authorization", owner()))
                .andExpect(status().isNotFound());
        String hidden = mockMvc.perform(get("/v1/suppliers").header("Authorization", owner())
                        .param("search", "Restore Me"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(tree(hidden).path("data").path("totalElements").asInt()).isZero();

        // ... but visible in the recycle bin.
        mockMvc.perform(get("/v1/suppliers/deleted").header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Restore Me Traders")));

        mockMvc.perform(post("/v1/suppliers/" + id + "/restore").header("Authorization", owner()))
                .andExpect(status().isNoContent());

        // Same id, back to ACTIVE, and the contact came back with it.
        mockMvc.perform(get("/v1/suppliers/" + id).header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value((int) id))
                .andExpect(jsonPath("$.data.supplierCode").value("SUP-9030"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.contacts[0].contactName").value("Restore Contact"));

        // Findable by ordinary search again, and gone from the deleted list.
        String afterRestore = mockMvc.perform(get("/v1/suppliers").header("Authorization", owner())
                        .param("search", "Restore Me"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(tree(afterRestore).path("data").path("totalElements").asInt()).isEqualTo(1);

        mockMvc.perform(get("/v1/suppliers/deleted").header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Restore Me Traders"))));

        // Restoring an already-restored row matches nothing and 404s, rather
        // than silently succeeding a second time.
        mockMvc.perform(post("/v1/suppliers/" + id + "/restore").header("Authorization", owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("CR-058: restoring a supplier that was never deleted gives 404")
    void restoreOfLiveSupplierIsNotFound() throws Exception {
        mockMvc.perform(post("/v1/suppliers/1/restore").header("Authorization", owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("CR-058: restoring an unknown id gives 404, indistinguishable from the above")
    void restoreOfUnknownIdIsNotFound() throws Exception {
        mockMvc.perform(post("/v1/suppliers/999999/restore").header("Authorization", owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("CR-058: SUPPLIER_VIEW alone cannot see the deleted list or restore - both need SUPPLIER_MANAGE")
    void deletedRecordsRequireManage() throws Exception {
        // ACCOUNTANT holds SUPPLIER_VIEW but not SUPPLIER_MANAGE.
        String accountant = bearer("9840223344", "Account@2026");

        mockMvc.perform(get("/v1/suppliers").header("Authorization", accountant))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/suppliers/deleted").header("Authorization", accountant))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/v1/suppliers/13/restore").header("Authorization", accountant))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("CR-058: an unauthenticated caller cannot reach the deleted list or restore")
    void deletedRecordsRejectAnonymous() throws Exception {
        mockMvc.perform(get("/v1/suppliers/deleted"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/suppliers/13/restore"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("only one contact per supplier can be primary")
    void onePrimaryContact() throws Exception {
        mockMvc.perform(post("/v1/suppliers/1/contacts").header("Authorization", owner())
                        .contentType(APPLICATION_JSON)
                        .content(json(new SupplierContactRequest(
                                "New Primary", "Partner", "9811100220", null, true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.primary").value(true));

        String body = mockMvc.perform(get("/v1/suppliers/1").header("Authorization", owner()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // jackson-databind 2.18 does not have JsonNode.valueStream() (added in 2.19).
        long primaries = StreamSupport.stream(
                        tree(body).path("data").path("contacts").spliterator(), false)
                .filter(node -> node.path("primary").asBoolean()).count();
        assertThat(primaries).isEqualTo(1);
    }

    @Test
    @DisplayName("STAFF cannot see suppliers at all")
    void staffForbidden() throws Exception {
        mockMvc.perform(get("/v1/suppliers")
                        .header("Authorization", bearer(STAFF_MOBILE, STAFF_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("ACCOUNTANT can view suppliers but cannot create one")
    void accountantIsReadOnly() throws Exception {
        String accountant = bearer("9840223344", "Account@2026");

        mockMvc.perform(get("/v1/suppliers").header("Authorization", accountant))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/suppliers").header("Authorization", accountant)
                        .contentType(APPLICATION_JSON)
                        .content(json(supplier("SUP-9020", "Should Fail",
                                "9811100230", null, "33"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unauthenticated request gives 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/v1/suppliers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an unknown supplier gives 404")
    void unknownSupplier() throws Exception {
        mockMvc.perform(get("/v1/suppliers/999999").header("Authorization", owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
