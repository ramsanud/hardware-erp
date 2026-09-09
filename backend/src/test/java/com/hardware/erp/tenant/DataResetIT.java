package com.hardware.erp.tenant;

import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.security.AppUserDetailsService;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.dto.DataResetPreviewResponse;
import com.hardware.erp.tenant.dto.DataResetRequest;
import com.hardware.erp.tenant.dto.DataResetResponse;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import com.hardware.erp.tenant.dto.TenantRegistrationResponse;
import com.hardware.erp.tenant.service.DataResetService;
import com.hardware.erp.tenant.service.TenantRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-067.
 *
 * Two shops are registered fresh in every test rather than resetting the
 * seeded tenant 1. That is not tidiness: AbstractIntegrationTest shares one
 * reused container across the whole suite with no rollback between methods, so
 * a reset aimed at tenant 1 would delete the products, stock and movements
 * every other integration test reads.
 *
 * The reset runs through the service with the SecurityContext populated by
 * hand, because a fresh shop's owner cannot reach the HTTP endpoint without
 * first completing a TOTP enrollment this test has no reason to exercise. The
 * endpoint's own authorization is covered separately, below, against the
 * seeded tenant using the read-only preview.
 */
class DataResetIT extends AbstractIntegrationTest {

    @Autowired private DataResetService dataResetService;
    @Autowired private TenantRegistrationService registrationService;
    @Autowired private AppUserDetailsService userDetailsService;
    @Autowired private JdbcTemplate jdbc;

    private Shop shopA;
    private Shop shopB;

    /** A registered shop plus the ids of the rows this test seeded into it. */
    private record Shop(Long tenantId, Long ownerUserId, String name,
                        Long customerId, Long productId, Long invoiceId, Long couponId) {}

    @BeforeEach
    void registerTwoShops() {
        shopA = registerShop("Reset Test Shop A");
        shopB = registerShop("Reset Test Shop B");
    }

    @Test
    @DisplayName("deletes the caller's transactional data and leaves every master record standing")
    void deletesTransactionsKeepsMasters() {
        authenticateAs(shopA);

        DataResetPreviewResponse preview = dataResetService.preview();
        assertThat(preview.shopName()).isEqualTo(shopA.name());
        assertThat(preview.totalRecords()).isPositive();

        DataResetResponse result = dataResetService.reset(
                new DataResetRequest(shopA.name(), null), new MockHttpServletRequest());

        assertThat(result.totalRecords()).isPositive();
        assertThat(count("invoice", shopA)).isZero();
        assertThat(count("payment", shopA)).isZero();
        assertThat(count("stock_movement", shopA)).isZero();
        assertThat(count("stock", shopA)).isZero();
        assertThat(count("notification_log", shopA)).isZero();

        // invoice_item has no tenant_id of its own - it is reached through a
        // subquery against invoice. If that subquery were ever dropped this is
        // the assertion that stays green while the one in the next test fails.
        assertThat(childCount("invoice_item", "invoice_id", "invoice", shopA)).isZero();

        // The masters the owner chose to keep.
        assertThat(count("product", shopA)).isEqualTo(1);
        assertThat(count("customer", shopA)).isEqualTo(1);
        assertThat(count("coupon", shopA)).isEqualTo(1);
        assertThat(count("app_user", shopA)).isEqualTo(1);
        assertThat(count("role", shopA)).isPositive();
    }

    @Test
    @DisplayName("another shop's data is untouched, including the child rows that carry no tenant_id")
    void doesNotTouchAnotherTenant() {
        authenticateAs(shopA);
        dataResetService.reset(new DataResetRequest(shopA.name(), null), new MockHttpServletRequest());

        assertThat(count("invoice", shopB)).isEqualTo(1);
        assertThat(count("payment", shopB)).isEqualTo(1);
        assertThat(count("stock_movement", shopB)).isEqualTo(1);
        assertThat(count("stock", shopB)).isEqualTo(1);
        assertThat(count("notification_log", shopB)).isEqualTo(1);
        assertThat(childCount("invoice_item", "invoice_id", "invoice", shopB)).isEqualTo(1);

        // The two UPDATEs are scoped the same way the DELETEs are, and would
        // fail silently rather than loudly if they were not.
        assertThat(couponTimesUsed(shopB)).isEqualTo(7);
        assertThat(nextInvoiceNumber(shopB)).isEqualTo(41);
    }

    @Test
    @DisplayName("counters and sequences describing the deleted data are restarted, master sequences are not")
    void restartsCountersThatWouldOtherwiseDescribeDeletedData() {
        authenticateAs(shopA);
        dataResetService.reset(new DataResetRequest(shopA.name(), null), new MockHttpServletRequest());

        // A coupon whose usage limit was consumed by invoices that no longer
        // exist would stay permanently exhausted.
        assertThat(couponTimesUsed(shopA)).isZero();
        // The next invoice in an empty shop is number 1, not 41.
        assertThat(nextInvoiceNumber(shopA)).isEqualTo(1);
        // Customers survive the reset, so their codes must not be reissued.
        assertThat(nextSequence(shopA, "CUSTOMER")).isEqualTo(12);
    }

    @Test
    @DisplayName("a confirmation phrase that is not the shop name deletes nothing")
    void wrongPhraseDeletesNothing() {
        authenticateAs(shopA);

        assertThatThrownBy(() -> dataResetService.reset(
                new DataResetRequest("Reset Test Shop B", null), new MockHttpServletRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not the shop's name");

        assertThat(count("invoice", shopA)).isEqualTo(1);
        assertThat(count("stock_movement", shopA)).isEqualTo(1);
    }

    @Test
    @DisplayName("the phrase tolerates case and stray whitespace but not a different name")
    void phraseIsTolerantNotLenient() {
        authenticateAs(shopA);

        DataResetResponse result = dataResetService.reset(
                new DataResetRequest("  reset test   SHOP a  ", null), new MockHttpServletRequest());

        assertThat(result.totalRecords()).isPositive();
        assertThat(count("invoice", shopA)).isZero();
    }

    @Test
    @DisplayName("a role without DATA_RESET cannot even see the preview")
    void previewRequiresTheNewPermission() throws Exception {
        // Read-only, so this one is safe to run against the seeded tenant.
        mockMvc.perform(get("/v1/settings/data-reset/preview")
                        .header("Authorization", bearer(MANAGER_MOBILE, MANAGER_PASSWORD)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/v1/settings/data-reset/preview")
                        .header("Authorization", bearer(OWNER_MOBILE, OWNER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopName").isNotEmpty())
                .andExpect(jsonPath("$.data.groups").isArray());
    }

    // =================================================================
    // fixture
    // =================================================================

    private Shop registerShop(String name) {
        String mobile = "9" + (100000000 + ThreadLocalRandom.current().nextInt(899999999));
        String email = "owner" + mobile + "@resettest.example";

        TenantRegistrationResponse registered = registrationService.register(new TenantRegistrationRequest(
                name, "Reset Test Owner", mobile, email, "Reset@2026",
                null, true, "1.0", "1.0", false));

        Long tenantId = registered.tenantId();
        Long ownerId = jdbc.queryForObject(
                "SELECT user_id FROM app_user WHERE tenant_id = ?", Long.class, tenantId);

        Long customerId = insertReturningId("""
                INSERT INTO customer (tenant_id, customer_code, customer_name, mobile_no, created_at)
                VALUES (?, 'CUST-0001', 'Reset Test Customer', '9000000001', now())
                RETURNING customer_id""", tenantId);

        Long productId = insertReturningId("""
                INSERT INTO product (tenant_id, product_code, product_name, unit, created_at)
                VALUES (?, 'PROD-0001', 'Reset Test Product', 'NOS', now())
                RETURNING product_id""", tenantId);

        Long couponId = insertReturningId("""
                INSERT INTO coupon (tenant_id, code, discount_type, discount_value,
                                    usage_limit, times_used, created_at)
                VALUES (?, 'RESETTEST', 'FLAT', 5000, 100, 7, now())
                RETURNING coupon_id""", tenantId);

        Long invoiceId = insertReturningId("""
                INSERT INTO invoice (tenant_id, invoice_number, customer_id, invoice_date,
                                     subtotal_paise, gst_amount_paise, total_paise,
                                     paid_paise, balance_paise, status, created_at)
                VALUES (?, 'INV-0040', %d, CURRENT_DATE, 10000, 1800, 11800, 0, 11800, 'UNPAID', now())
                RETURNING invoice_id""".formatted(customerId), tenantId);

        jdbc.update("""
                INSERT INTO invoice_item (invoice_id, product_id, product_name_snapshot, unit,
                                          quantity, unit_price_paise, gst_rate_percent,
                                          line_subtotal_paise, line_gst_paise, line_total_paise)
                VALUES (?, ?, 'Reset Test Product', 'NOS', 1, 10000, 18, 10000, 1800, 11800)""",
                invoiceId, productId);

        jdbc.update("""
                INSERT INTO payment (tenant_id, invoice_id, payment_date, amount_paise,
                                     payment_method, created_at)
                VALUES (?, ?, now(), 11800, 'CASH', now())""", tenantId, invoiceId);

        jdbc.update("""
                INSERT INTO stock (tenant_id, product_id, quantity_on_hand, created_at)
                VALUES (?, ?, 10, now())""", tenantId, productId);

        jdbc.update("""
                INSERT INTO stock_movement (tenant_id, product_id, movement_type, quantity_change,
                                            balance_after, created_at)
                VALUES (?, ?, 'INITIAL', 10, 10, now())""", tenantId, productId);

        jdbc.update("""
                INSERT INTO notification_log (tenant_id, channel, recipient, body, status, created_at)
                VALUES (?, 'EMAIL', 'someone@example.com', 'Invoice INV-0040', 'LOGGED_ONLY', now())""",
                tenantId);

        // Registration seeds no sequences, so both a transactional type (which
        // must restart) and a master type (which must not) are set up here at
        // recognisably non-default values.
        jdbc.update("""
                INSERT INTO document_sequence (tenant_id, doc_type, next_value, created_at)
                VALUES (?, 'INVOICE', 41, now()), (?, 'CUSTOMER', 12, now())""", tenantId, tenantId);

        return new Shop(tenantId, ownerId, name, customerId, productId, invoiceId, couponId);
    }

    private Long insertReturningId(String sql, Long tenantId) {
        return jdbc.queryForObject(sql, Long.class, tenantId);
    }

    private void authenticateAs(Shop shop) {
        AppUserDetails principal = userDetailsService.loadById(shop.ownerUserId()).orElseThrow();
        assertThat(principal.hasPermission("DATA_RESET"))
                .as("a newly registered shop's OWNER must receive DATA_RESET without a manual grant")
                .isTrue();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private long count(String table, Shop shop) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Long.class, shop.tenantId());
        return value == null ? 0 : value;
    }

    private long childCount(String childTable, String fkColumn, String parentTable, Shop shop) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + childTable + " WHERE " + fkColumn + " IN "
                        + "(SELECT " + fkColumn + " FROM " + parentTable + " WHERE tenant_id = ?)",
                Long.class, shop.tenantId());
        return value == null ? 0 : value;
    }

    private long couponTimesUsed(Shop shop) {
        Long value = jdbc.queryForObject(
                "SELECT times_used FROM coupon WHERE coupon_id = ?", Long.class, shop.couponId());
        return value == null ? 0 : value;
    }

    private long nextInvoiceNumber(Shop shop) {
        return nextSequence(shop, "INVOICE");
    }

    private long nextSequence(Shop shop, String docType) {
        Long value = jdbc.queryForObject(
                "SELECT next_value FROM document_sequence WHERE tenant_id = ? AND doc_type = ?",
                Long.class, shop.tenantId(), docType);
        return value == null ? 0 : value;
    }
}
