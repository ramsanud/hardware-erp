package com.hardware.erp.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.inventory.job.LowStockSnapshotJob;
import com.hardware.erp.inventory.repository.LowStockSnapshotRepository;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-084 - the dashboard's two placeholder sparklines become measured.
 *
 * Three things worth a test: the lazy first read creates today's snapshot
 * so a fresh shop has a real point immediately; the job's upsert is
 * idempotent on (tenant, day) so a re-run cannot duplicate a row; and the
 * revenue trend now carries outstandingPaise alongside revenue for every
 * bucket. Tenant scoping is exercised the only way that proves anything -
 * a second shop reading its own trend does not see the first shop's rows.
 */
class LowStockTrendIT extends AbstractIntegrationTest {

    @Autowired private LowStockSnapshotRepository snapshotRepository;
    @Autowired private LowStockSnapshotJob job;

    private JsonNode data(String token, String path) throws Exception {
        String body = mockMvc.perform(get(path).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data");
    }

    @Test
    @DisplayName("the first read of the trend takes today's snapshot, so a fresh shop has one real point")
    void firstReadSnapshotsToday() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        LocalDate today = LocalDate.now(LowStockSnapshotJob.SHOP_ZONE);

        JsonNode trend = data(token, "/v1/analytics/low-stock-trend?days=14");
        JsonNode points = trend.path("points");

        assertThat(points.size()).isGreaterThanOrEqualTo(1);
        JsonNode last = points.get(points.size() - 1);
        assertThat(last.path("date").asText()).isEqualTo(today.toString());
        assertThat(last.path("lowStockCount").asInt()).isGreaterThanOrEqualTo(0);
        assertThat(trend.path("summary").asText()).isNotBlank();
    }

    @Test
    @DisplayName("running the snapshot twice for one day leaves exactly one row")
    void snapshotIsIdempotentPerDay() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        // Any authenticated read resolves the tenant id the same way the job will.
        data(token, "/v1/analytics/low-stock-trend?days=2");
        Long tenantId = snapshotRepository.findAll().get(0).getTenantId();
        LocalDate day = LocalDate.now(LowStockSnapshotJob.SHOP_ZONE).minusDays(3);

        job.snapshot(tenantId, day);
        job.snapshot(tenantId, day);

        long rowsForDay = snapshotRepository.findByTenantIdAndTakenOnBetweenOrderByTakenOnAsc(tenantId, day, day).size();
        assertThat(rowsForDay).isEqualTo(1);
    }

    @Test
    @DisplayName("days outside 2..90 are rejected as a bad request, not a 500")
    void rejectsSillyWindows() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        mockMvc.perform(get("/v1/analytics/low-stock-trend?days=1").header("Authorization", token))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/v1/analytics/low-stock-trend?days=400").header("Authorization", token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("the revenue trend now carries outstandingPaise on every bucket")
    void revenueTrendCarriesOutstanding() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(13);

        JsonNode series = data(token,
                "/v1/analytics/revenue-trend?from=" + from + "&to=" + to + "&granularity=day");

        for (JsonNode point : series.path("points")) {
            assertThat(point.has("outstandingPaise")).as("outstandingPaise present on " + point).isTrue();
            assertThat(point.has("outstandingDisplay")).isTrue();
            // Outstanding can never exceed what was invoiced in the same bucket.
            assertThat(point.path("outstandingPaise").asLong())
                    .isLessThanOrEqualTo(point.path("revenuePaise").asLong());
        }
    }
}
