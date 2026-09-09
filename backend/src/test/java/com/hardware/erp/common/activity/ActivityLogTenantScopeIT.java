package com.hardware.erp.common.activity;

import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-072. The whole point of this feature is that a shop reads its OWN
 * business history and nothing else, so tenant scope is what these tests are
 * for - not the paging or the field mapping.
 *
 * Rows are written straight through the repository with explicit tenant ids.
 * Going through ActivityLogService would take the tenant from the JWT, which
 * is exactly the thing under test - it could not then produce a foreign row to
 * prove the query excludes it.
 *
 * Every row is tagged with an entityType unique to the run and deleted
 * afterwards, so the suite's reused container does not accumulate history (the
 * lesson ProjectMaterialStockIT records: an IT that writes rows a later test
 * can see must be idempotent, not merely correct).
 */
class ActivityLogTenantScopeIT extends AbstractIntegrationTest {

    @Autowired private ActivityLogRepository activityLogRepository;
    @Autowired private ActivityLogQueryService activityLogQueryService;
    @Autowired private UserRepository userRepository;

    private final String marker = "ITProbe" + UUID.randomUUID().toString().substring(0, 8);

    /** No tenant has this id. Cheaper and safer than registering a second shop. */
    private static final Long FOREIGN_TENANT_ID = 999_999L;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        activityLogRepository.deleteAll(
                activityLogRepository.findAll().stream()
                        .filter(row -> marker.equals(row.getEntityType()))
                        .toList());
    }

    private void authenticateAs(String mobile) {
        User user = userRepository.findByIdentifier(mobile).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(user), null, List.of()));
    }

    private Long ownTenantId() {
        return userRepository.findByIdentifier(OWNER_MOBILE).orElseThrow().getTenant().getId();
    }

    private void writeRow(Long tenantId, String label) {
        activityLogRepository.save(ActivityLog.builder()
                .tenantId(tenantId)
                .moduleCode("ITMOD")
                .entityType(marker)
                .entityId(1L)
                .entityLabel(label)
                .action(ActivityAction.CREATE)
                .newValues(Map.of("name", label))
                .fullName("IT")
                .build());
    }

    @Test
    @DisplayName("CR-072: the viewer returns this shop's rows, never another shop's, never an unattributed one")
    void scopedToTheCallersOwnTenant() {
        writeRow(ownTenantId(), "mine");
        writeRow(FOREIGN_TENANT_ID, "another shop's");
        writeRow(null, "written by a scheduled job");

        authenticateAs(OWNER_MOBILE);
        PageResponse<ActivityLogResponse> page = activityLogQueryService.search(
                null, marker, null, null, null, null, PageRequest.of(0, 50));

        assertThat(page.content()).extracting(ActivityLogResponse::entityLabel)
                .as("only the caller's own row is visible")
                .containsExactly("mine");
    }

    @Test
    @DisplayName("CR-072: a null tenant_id row is readable by nobody, which is why the column is nullable")
    void unattributedRowsAreInvisibleToEveryone() {
        writeRow(null, "orphan");

        authenticateAs(OWNER_MOBILE);
        assertThat(activityLogQueryService.search(null, marker, null, null, null, null,
                PageRequest.of(0, 50)).content())
                .as("NULL never equals anything in SQL - the safety property V55 relies on")
                .isEmpty();
    }

    @Test
    @DisplayName("CR-072: the module filter offers only modules this shop has history for")
    void moduleCodesAreScopedToo() {
        writeRow(FOREIGN_TENANT_ID, "another shop's");

        authenticateAs(OWNER_MOBILE);
        assertThat(activityLogQueryService.moduleCodes())
                .as("a module only the other shop has used must not leak through the filter list")
                .doesNotContain("ITMOD");
    }

    @Test
    @DisplayName("CR-072: GET /v1/activity-log returns the caller's own history over HTTP")
    void endpointReturnsOwnHistory() throws Exception {
        writeRow(ownTenantId(), "over http");
        writeRow(FOREIGN_TENANT_ID, "not mine");

        mockMvc.perform(get("/v1/activity-log")
                        .header("Authorization", bearer(OWNER_MOBILE, OWNER_PASSWORD))
                        .param("entityType", marker)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].entityLabel").value("over http"));
    }

    @Test
    @DisplayName("CR-072: the endpoint refuses a caller without AUDIT_VIEW")
    void endpointRequiresAuditView() throws Exception {
        // STAFF holds no AUDIT_VIEW - the gate is the server's, not the UI's.
        mockMvc.perform(get("/v1/activity-log")
                        .header("Authorization", bearer(STAFF_MOBILE, STAFF_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CR-072: the tenant stamped on a write is the signed-in user's own")
    void writePathStampsTenantFromTheToken() {
        authenticateAs(OWNER_MOBILE);

        ActivityLog written = activityLogRepository.save(ActivityLog.builder()
                .tenantId(SecurityUtils.currentTenantId().orElse(null))
                .moduleCode("ITMOD")
                .entityType(marker)
                .action(ActivityAction.CREATE)
                .fullName("IT")
                .build());

        assertThat(written.getTenantId()).isEqualTo(ownTenantId());
    }
}
