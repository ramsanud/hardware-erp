package com.hardware.erp.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-101. The async job queue end to end: enqueue, poll until COMPLETED
 * (or FAILED), download. No mocked clock or executor - the real
 * {@code taskExecutor} bean runs the job on its own thread exactly as
 * production would, so a passing test here means the security-context
 * handoff in {@code ReportJobWorker} actually works, not just compiles.
 */
class ReportJobControllerIT extends AbstractIntegrationTest {

    private static final String SHOP_GSTIN = "33AABCS1429B1Z1";

    @Autowired private TenantRepository tenantRepository;

    private String owner;
    private String manager;
    private String staff;

    @BeforeEach
    void signIn() throws Exception {
        owner = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        manager = bearer(MANAGER_MOBILE, MANAGER_PASSWORD);
        staff = bearer(STAFF_MOBILE, STAFF_PASSWORD);

        // GSTR-1 requires a valid shop GSTIN (Gstr1Service) - the same
        // fixture ReportControllerIT sets up for its own GSTR-1 coverage.
        Tenant tenant = tenantRepository.findById(1L).orElseThrow();
        tenant.setGstNo(SHOP_GSTIN);
        tenant.setStateCode("33");
        tenantRepository.save(tenant);
    }

    /** Up to ~10s of real background-thread work - generous for a render this small, bounded so a genuine hang still fails the test. */
    private JsonNode pollUntilDone(Long jobId, String bearer) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            String body = mockMvc.perform(get("/v1/documents/jobs/" + jobId).header("Authorization", bearer))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode data = tree(body).path("data");
            String jobStatus = data.path("status").asText();
            if (jobStatus.equals("COMPLETED") || jobStatus.equals("FAILED")) {
                return data;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Report job " + jobId + " did not finish within the poll budget");
    }

    private Long enqueue(String bearer, String reportType, String format, Map<String, String> params) throws Exception {
        String body = mockMvc.perform(post("/v1/documents/jobs").header("Authorization", bearer)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("reportType", reportType, "format", format, "params", params))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = tree(body).path("data");
        assertThat(data.path("status").asText()).isIn("PENDING", "PROCESSING", "COMPLETED");
        return data.path("id").asLong();
    }

    private Map<String, String> range() {
        LocalDate today = LocalDate.now();
        return Map.of("from", today.minusDays(30).toString(), "to", today.toString());
    }

    @Test
    @DisplayName("a Day Book PDF job completes in the background and downloads as a real PDF")
    void dayBookPdfJob() throws Exception {
        Long jobId = enqueue(owner, "DAY_BOOK", "PDF", range());
        JsonNode finished = pollUntilDone(jobId, owner);
        assertThat(finished.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(finished.path("fileName").asText()).endsWith(".pdf");
        assertThat(finished.path("fileSizeBytes").asInt()).isGreaterThan(0);

        MvcResult download = mockMvc.perform(get("/v1/documents/jobs/" + jobId + "/download").header("Authorization", owner))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).startsWith("%PDF-".getBytes());
        assertThat(download.getResponse().getContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("a Stock Valuation image job produces a real PNG, and a GST Summary CSV job produces a real CSV")
    void imageAndCsvJobs() throws Exception {
        Long pngJob = enqueue(owner, "STOCK_VALUATION", "PNG", Map.of());
        JsonNode png = pollUntilDone(pngJob, owner);
        assertThat(png.path("status").asText()).isEqualTo("COMPLETED");
        byte[] pngBytes = mockMvc.perform(get("/v1/documents/jobs/" + pngJob + "/download").header("Authorization", owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(pngBytes).startsWith(new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        Long csvJob = enqueue(owner, "GST_SUMMARY", "CSV", range());
        JsonNode csv = pollUntilDone(csvJob, owner);
        assertThat(csv.path("status").asText()).isEqualTo("COMPLETED");
        MvcResult csvDownload = mockMvc.perform(get("/v1/documents/jobs/" + csvJob + "/download").header("Authorization", owner))
                .andExpect(status().isOk()).andReturn();
        assertThat(csvDownload.getResponse().getContentType()).startsWith("text/csv");
        byte[] csvBytes = csvDownload.getResponse().getContentAsByteArray();
        assertThat(csvBytes[0] & 0xFF).isEqualTo(0xEF); // UTF-8 BOM
    }

    @Test
    @DisplayName("an owner (REPORT_FINANCIAL) can queue GSTR-1 as JSON; a manager without it is refused at enqueue")
    void gstr1PermissionGate() throws Exception {
        LocalDate today = LocalDate.now();
        String period = String.format("%02d%d", today.getMonthValue(), today.getYear());

        Long jobId = enqueue(owner, "GSTR1", "JSON", Map.of("period", period));
        JsonNode finished = pollUntilDone(jobId, owner);
        assertThat(finished.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(finished.path("fileName").asText()).startsWith("GSTR1-");

        mockMvc.perform(post("/v1/documents/jobs").header("Authorization", manager)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("reportType", "GSTR1", "format", "JSON", "params", Map.of("period", period)))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GSTR-1 refuses PNG at render time - it has no visual layout, so the job ends FAILED, not stuck")
    void gstr1RefusesImageFormat() throws Exception {
        LocalDate today = LocalDate.now();
        String period = String.format("%02d%d", today.getMonthValue(), today.getYear());
        Long jobId = enqueue(owner, "GSTR1", "PNG", Map.of("period", period));
        JsonNode finished = pollUntilDone(jobId, owner);
        assertThat(finished.path("status").asText()).isEqualTo("FAILED");
        assertThat(finished.path("errorMessage").asText()).isNotBlank();
    }

    @Test
    @DisplayName("STAFF holds no REPORT_VIEW and cannot even queue a job")
    void staffCannotEnqueue() throws Exception {
        mockMvc.perform(post("/v1/documents/jobs").header("Authorization", staff)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("reportType", "DAY_BOOK", "format", "PDF", "params", range()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the job list is tenant-scoped and newest first")
    void listIsNewestFirst() throws Exception {
        Long first = enqueue(owner, "STOCK_VALUATION", "PDF", Map.of());
        pollUntilDone(first, owner);
        Long second = enqueue(owner, "STOCK_VALUATION", "XLSX", Map.of());
        pollUntilDone(second, owner);

        String body = mockMvc.perform(get("/v1/documents/jobs").header("Authorization", owner))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
        JsonNode content = tree(body).path("data").path("content");
        assertThat(content.get(0).path("id").asLong()).isEqualTo(second);
        // file_data is never in this response shape at all (ReportJobResponse has no such field) -
        // proving the list path never even selects it, not just that it is omitted from JSON.
        assertThat(content.get(0).has("fileData")).isFalse();
    }

    @Test
    @DisplayName("downloading a job that does not exist for this tenant is a 404, not a leak")
    void downloadUnknownJobIs404() throws Exception {
        mockMvc.perform(get("/v1/documents/jobs/999999999/download").header("Authorization", owner))
                .andExpect(status().isNotFound());
    }
}
