package com.hardware.erp.backup;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.backup.service.TenantBackupService;
import com.hardware.erp.summary.DailyBusinessSummaryService;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CR-092 backups and the daily summary, against real PostgreSQL. */
class TenantBackupIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantBackupService backupService;
    @Autowired private DailyBusinessSummaryService summaryService;

    private record Shop(String bearer, Long tenantId) {}

    private Shop registerShop(SubscriptionTier tier) throws Exception {
        String mobile = "9" + (100000000 + ThreadLocalRandom.current().nextInt(899999999));
        String body = mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(json(new TenantRegistrationRequest("Backup Shop " + mobile.substring(5), "Owner",
                                mobile, "owner" + mobile + "@backuptest.example", "Backup@2026",
                                tier, true, "1.0", "1.0", false, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Shop(bearer(mobile, "Backup@2026"), tree(body).path("data").path("tenantId").asLong());
    }

    @Test
    @DisplayName("an on-demand backup is recorded, downloadable, and contains this shop's rows only")
    void manualBackupIsRecordedAndDownloadable() throws Exception {
        Shop shop = registerShop(SubscriptionTier.MAX);
        Shop other = registerShop(SubscriptionTier.MAX);
        jdbc.update("""
                INSERT INTO product (tenant_id, product_code, product_name, unit, selling_price_paise, purchase_price_paise, mrp_paise, gst_rate_percent, status, created_at)
                VALUES (?, 'BK-1', 'Backup Only Product', 'PCS', 100, 50, 100, 0, 'ACTIVE', now())""", shop.tenantId());
        jdbc.update("""
                INSERT INTO product (tenant_id, product_code, product_name, unit, selling_price_paise, purchase_price_paise, mrp_paise, gst_rate_percent, status, created_at)
                VALUES (?, 'BK-2', 'Other Shops Secret Product', 'PCS', 100, 50, 100, 0, 'ACTIVE', now())""", other.tenantId());

        String body = mockMvc.perform(post("/v1/backups").param("format", "JSON").header("Authorization", shop.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.triggerType").value("MANUAL"))
                .andReturn().getResponse().getContentAsString();
        JsonNode backup = tree(body).path("data");
        assertThat(backup.path("recordCount").asInt()).isGreaterThanOrEqualTo(1);
        long id = backup.path("id").asLong();

        mockMvc.perform(get("/v1/backups").header("Authorization", shop.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(id));

        byte[] file = mockMvc.perform(get("/v1/backups/" + id + "/download").header("Authorization", shop.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String content = new String(file, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("Backup Only Product").doesNotContain("Other Shops Secret Product");

        // Another shop cannot download it.
        mockMvc.perform(get("/v1/backups/" + id + "/download").header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the nightly job backs up a Premium shop and prunes to the newest seven; a Basic shop gets nothing automatic")
    void scheduledBackupHonoursPlanAndPrunes() throws Exception {
        Shop premium = registerShop(SubscriptionTier.MAX);
        Shop basic = registerShop(SubscriptionTier.FREE);

        for (int i = 0; i < 9; i++) {
            backupService.scheduled(premium.tenantId());
        }
        backupService.scheduled(basic.tenantId());

        Integer premiumRows = jdbc.queryForObject("SELECT COUNT(*) FROM tenant_backup WHERE tenant_id = ?", Integer.class, premium.tenantId());
        Integer basicRows = jdbc.queryForObject("SELECT COUNT(*) FROM tenant_backup WHERE tenant_id = ?", Integer.class, basic.tenantId());
        assertThat(premiumRows).isEqualTo(7);
        assertThat(basicRows).isZero();
    }

    @Test
    @DisplayName("the daily summary reports the day's recorded figures and leaves an in-app notification")
    void dailySummaryReflectsTheDay() throws Exception {
        Shop shop = registerShop(SubscriptionTier.MAX);
        Long productId = jdbc.queryForObject("""
                INSERT INTO product (tenant_id, product_code, product_name, unit, selling_price_paise, purchase_price_paise, mrp_paise, gst_rate_percent, status, created_at)
                VALUES (?, 'DS-1', 'Summary Item', 'PCS', 100000, 60000, 100000, 0, 'ACTIVE', now()) RETURNING product_id""",
                Long.class, shop.tenantId());
        jdbc.update("INSERT INTO stock (tenant_id, product_id, quantity_on_hand, created_at) VALUES (?, ?, 10, now())", shop.tenantId(), productId);
        mockMvc.perform(post("/v1/invoices").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new com.hardware.erp.invoice.dto.InvoiceRequest("Summary Customer", "9800006001", null, null, null,
                                java.util.List.of(new com.hardware.erp.invoice.dto.InvoiceItemRequest(productId, new java.math.BigDecimal("2"))),
                                50000L, com.hardware.erp.invoice.entity.PaymentMethod.CASH, null))))
                .andExpect(status().isCreated());

        String preview = summaryService.preview(shop.tenantId(), LocalDate.now());
        assertThat(preview).contains("1 invoice, ₹2,000.00").contains("Payments received: ₹500.00")
                .contains("Total outstanding from customers: ₹1,500.00");

        mockMvc.perform(get("/v1/daily-summary/today").header("Authorization", shop.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value(org.hamcrest.Matchers.containsString("₹2,000.00")));

        mockMvc.perform(post("/v1/daily-summary/send").header("Authorization", shop.bearer()))
                .andExpect(status().isOk());
        Integer notifications = jdbc.queryForObject(
                "SELECT COUNT(*) FROM owner_notification WHERE tenant_id = ? AND notification_type = 'DAILY_SUMMARY'",
                Integer.class, shop.tenantId());
        assertThat(notifications).isEqualTo(1);
    }
}
