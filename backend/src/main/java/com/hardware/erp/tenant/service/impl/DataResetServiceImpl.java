package com.hardware.erp.tenant.service.impl;

import com.hardware.erp.auth.entity.AuditAction;
import com.hardware.erp.auth.service.SecurityAuditService;
import com.hardware.erp.common.activity.ActivityAction;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.security.captcha.CaptchaService;
import com.hardware.erp.tenant.dto.DataResetPreviewResponse;
import com.hardware.erp.tenant.dto.DataResetPreviewResponse.Group;
import com.hardware.erp.tenant.dto.DataResetRequest;
import com.hardware.erp.tenant.dto.DataResetResponse;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.service.DataResetService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CR-067. The one destructive operation in the application that crosses module
 * boundaries, so it is written out in a single place where the order can be
 * read top to bottom rather than inferred from twelve services.
 *
 * Three properties this class exists to guarantee, ordered by how badly a bug
 * in each would end:
 *
 * 1. It can only ever touch the caller's own tenant. Every statement is scoped
 *    by requireCurrentTenantId(), and no method here accepts a tenant id.
 * 2. It can only ever touch the tables named in DELETE_STATEMENTS. That list
 *    is a private constant; nothing a client sends reaches a table or column
 *    name, so there is nothing here to inject into.
 * 3. It is all or nothing. One transaction - a half-reset shop, invoices gone
 *    and their payments still standing, would be worse than either outcome.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataResetServiceImpl implements DataResetService {

    private static final String MODULE = "SETTINGS";
    private static final String ENTITY = "TENANT";
    private static final String CONFIRMATION_FAILED = "RESET_CONFIRMATION_FAILED";

    /**
     * The deletes, in foreign-key order. Table name to statement.
     *
     * Child tables carry no tenant_id of their own - that is deliberate, they
     * are isolated through their parent - so each is scoped by a subquery
     * against its already-scoped parent rather than by a column it does not
     * have.
     *
     * Most of those children also cascade from their parent, and leaning on
     * that would have made this list a third the length. Two reasons it is
     * written out instead: a cascade reports no row count, so the response
     * could not honestly say what it removed; and credit_note_item references
     * invoice_item, so credit notes genuinely must go before invoices or the
     * transaction dies on a constraint - an ordering no cascade would arrange.
     */
    private static final Map<String, String> DELETE_STATEMENTS = new LinkedHashMap<>();

    static {
        // Credit notes first of all: credit_note_item points at invoice_item.
        DELETE_STATEMENTS.put("credit_note_item",
                "DELETE FROM credit_note_item WHERE credit_note_id IN "
                        + "(SELECT credit_note_id FROM credit_note WHERE tenant_id = :tenantId)");
        DELETE_STATEMENTS.put("credit_note",
                "DELETE FROM credit_note WHERE tenant_id = :tenantId");

        // A challan points at both the sales order and the invoice.
        DELETE_STATEMENTS.put("delivery_challan_item",
                "DELETE FROM delivery_challan_item WHERE delivery_challan_id IN "
                        + "(SELECT delivery_challan_id FROM delivery_challan WHERE tenant_id = :tenantId)");
        DELETE_STATEMENTS.put("delivery_challan",
                "DELETE FROM delivery_challan WHERE tenant_id = :tenantId");

        DELETE_STATEMENTS.put("sales_order_item",
                "DELETE FROM sales_order_item WHERE sales_order_id IN "
                        + "(SELECT sales_order_id FROM sales_order WHERE tenant_id = :tenantId)");
        DELETE_STATEMENTS.put("sales_order",
                "DELETE FROM sales_order WHERE tenant_id = :tenantId");

        DELETE_STATEMENTS.put("payment",
                "DELETE FROM payment WHERE tenant_id = :tenantId");

        // A quotation keeps a link to the invoice it became.
        DELETE_STATEMENTS.put("quotation_item",
                "DELETE FROM quotation_item WHERE quotation_id IN "
                        + "(SELECT quotation_id FROM quotation WHERE tenant_id = :tenantId)");
        DELETE_STATEMENTS.put("quotation",
                "DELETE FROM quotation WHERE tenant_id = :tenantId");

        DELETE_STATEMENTS.put("invoice_item",
                "DELETE FROM invoice_item WHERE invoice_id IN "
                        + "(SELECT invoice_id FROM invoice WHERE tenant_id = :tenantId)");
        DELETE_STATEMENTS.put("invoice",
                "DELETE FROM invoice WHERE tenant_id = :tenantId");

        DELETE_STATEMENTS.put("purchase_document",
                "DELETE FROM purchase_document WHERE tenant_id = :tenantId");
        DELETE_STATEMENTS.put("purchase_payment",
                "DELETE FROM purchase_payment WHERE tenant_id = :tenantId");
        DELETE_STATEMENTS.put("purchase_item",
                "DELETE FROM purchase_item WHERE purchase_id IN "
                        + "(SELECT purchase_id FROM purchase WHERE tenant_id = :tenantId)");
        DELETE_STATEMENTS.put("purchase",
                "DELETE FROM purchase WHERE tenant_id = :tenantId");

        DELETE_STATEMENTS.put("expense_receipt",
                "DELETE FROM expense_receipt WHERE business_expense_id IN "
                        + "(SELECT business_expense_id FROM business_expense WHERE tenant_id = :tenantId)");
        DELETE_STATEMENTS.put("business_expense",
                "DELETE FROM business_expense WHERE tenant_id = :tenantId");

        // Attendance points at project as well as worker, so it goes first.
        DELETE_STATEMENTS.put("worker_attendance",
                "DELETE FROM worker_attendance WHERE tenant_id = :tenantId");
        DELETE_STATEMENTS.put("worker_payment",
                "DELETE FROM worker_payment WHERE tenant_id = :tenantId");

        DELETE_STATEMENTS.put("project_material",
                "DELETE FROM project_material WHERE tenant_id = :tenantId");
        DELETE_STATEMENTS.put("project_expense",
                "DELETE FROM project_expense WHERE tenant_id = :tenantId");
        DELETE_STATEMENTS.put("project_payment",
                "DELETE FROM project_payment WHERE tenant_id = :tenantId");
        DELETE_STATEMENTS.put("project",
                "DELETE FROM project WHERE tenant_id = :tenantId");

        // The ledger and the balance go together. The stock rows are deleted
        // rather than zeroed: StockServiceImpl already creates them lazily, so
        // the next movement recreates the row at zero - and zeroing a balance
        // while deleting the movements that produced it would leave the two
        // disagreeing about how it got there.
        DELETE_STATEMENTS.put("stock_movement",
                "DELETE FROM stock_movement WHERE tenant_id = :tenantId");
        DELETE_STATEMENTS.put("stock",
                "DELETE FROM stock WHERE tenant_id = :tenantId");

        DELETE_STATEMENTS.put("notification_log",
                "DELETE FROM notification_log WHERE tenant_id = :tenantId");
        // A replayed response quoting an invoice that no longer exists would
        // hand a stale body back to any retry that arrives after the reset.
        DELETE_STATEMENTS.put("idempotency_record",
                "DELETE FROM idempotency_record WHERE tenant_id = :tenantId");
    }

    /**
     * A counter on a record the reset KEEPS, describing data it destroys. Left
     * alone, a coupon with usage_limit 100 and 100 wiped redemptions stays
     * permanently exhausted against invoices that no longer exist. Not in the
     * original ask - found by following the workflow through (CR-067).
     */
    private static final String RESET_COUPON_USAGE =
            "UPDATE coupon SET times_used = 0 WHERE tenant_id = :tenantId AND times_used <> 0";

    /**
     * Transactional document types only. CUSTOMER, SUPPLIER, PRODUCT, CATEGORY
     * and BRAND are left alone on purpose - those records survive the reset, so
     * restarting their numbering would collide with codes still in use.
     */
    private static final String RESET_DOCUMENT_SEQUENCES =
            "UPDATE document_sequence SET next_value = 1 "
                    + "WHERE tenant_id = :tenantId "
                    + "AND doc_type IN ('INVOICE', 'QUOTATION', 'PURCHASE', 'PROJECT') "
                    + "AND next_value <> 1";

    /**
     * Counted for the preview, and reported back after the delete. Parent
     * tables only - a shop owner counts invoices, not invoice lines.
     */
    private static final Map<String, String> REPORTED_GROUPS = new LinkedHashMap<>();

    static {
        REPORTED_GROUPS.put("Invoices", "invoice");
        REPORTED_GROUPS.put("Payments received", "payment");
        REPORTED_GROUPS.put("Credit notes", "credit_note");
        REPORTED_GROUPS.put("Delivery challans", "delivery_challan");
        REPORTED_GROUPS.put("Sales orders", "sales_order");
        REPORTED_GROUPS.put("Quotations", "quotation");
        REPORTED_GROUPS.put("Purchase bills", "purchase");
        REPORTED_GROUPS.put("Purchase payments", "purchase_payment");
        REPORTED_GROUPS.put("Business expenses", "business_expense");
        REPORTED_GROUPS.put("Projects", "project");
        REPORTED_GROUPS.put("Attendance records", "worker_attendance");
        REPORTED_GROUPS.put("Worker payments", "worker_payment");
        REPORTED_GROUPS.put("Stock movements", "stock_movement");
    }

    @PersistenceContext
    private EntityManager entityManager;

    private final TenantRepository tenantRepository;
    private final CaptchaService captchaService;
    private final SecurityAuditService securityAudit;
    private final ActivityLogService activityLog;

    @Override
    @Transactional(readOnly = true)
    public DataResetPreviewResponse preview() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Tenant tenant = currentTenant(tenantId);

        List<Group> groups = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, String> entry : REPORTED_GROUPS.entrySet()) {
            long count = countRows(entry.getValue(), tenantId);
            total += count;
            groups.add(new Group(entry.getKey(), count));
        }

        return new DataResetPreviewResponse(tenant.getName(), groups, total, captchaService.active());
    }

    @Override
    @Transactional
    public DataResetResponse reset(DataResetRequest request, HttpServletRequest httpRequest) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        AppUserDetails caller = SecurityUtils.requireCurrentUser();
        Tenant tenant = currentTenant(tenantId);

        // CAPTCHA first, exactly as the login endpoint orders it: a caller who
        // cannot pass the challenge must not learn whether their phrase was
        // right either. It is a no-op when no challenge is configured, which
        // is precisely why the typed phrase below is not optional.
        captchaService.verify(request.captchaToken(), SecurityUtils.clientIp(httpRequest));
        requireMatchingPhrase(request.confirmationPhrase(), tenant, caller);

        Map<String, Long> deleted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : DELETE_STATEMENTS.entrySet()) {
            int rows = entityManager.createNativeQuery(entry.getValue())
                    .setParameter("tenantId", tenantId)
                    .executeUpdate();
            deleted.put(entry.getKey(), (long) rows);
        }

        int couponsReset = entityManager.createNativeQuery(RESET_COUPON_USAGE)
                .setParameter("tenantId", tenantId).executeUpdate();
        int sequencesReset = entityManager.createNativeQuery(RESET_DOCUMENT_SEQUENCES)
                .setParameter("tenantId", tenantId).executeUpdate();

        List<Group> groups = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, String> entry : REPORTED_GROUPS.entrySet()) {
            long count = deleted.getOrDefault(entry.getValue(), 0L);
            total += count;
            groups.add(new Group(entry.getKey(), count));
        }

        // Both logs survive the reset by design (hard rule 8). A wipe that
        // also erased the record of who performed it would not be an audit
        // trail, and the confirmation dialog says so before it is run.
        String summary = "Reset shop data: " + total + " records deleted, "
                + couponsReset + " coupon usage counters and "
                + sequencesReset + " document sequences restarted";
        log.warn("Tenant {} data reset by user {} - {}", tenantId, caller.getId(), summary);
        securityAudit.success(AuditAction.DATA_RESET, caller.getId(), caller.getFullName(),
                ENTITY, tenantId);
        activityLog.action(MODULE, ENTITY, tenantId, tenant.getName(),
                ActivityAction.DELETE, summary);

        return new DataResetResponse(groups, total);
    }

    /**
     * Case-insensitive and whitespace-tolerant on purpose. The phrase is a
     * deliberateness check, not a password - rejecting an owner who typed
     * their own shop name with a trailing space only teaches them to paste it,
     * which is exactly what asking them to type it is meant to prevent.
     */
    private void requireMatchingPhrase(String typed, Tenant tenant, AppUserDetails caller) {
        if (!normalise(tenant.getName()).equals(normalise(typed))) {
            securityAudit.failure(AuditAction.DATA_RESET, caller.getId(), caller.getFullName(),
                    "Confirmation phrase did not match the shop name");
            throw new BusinessException(
                    "That is not the shop's name. Type it exactly as shown to confirm.",
                    HttpStatus.BAD_REQUEST, CONFIRMATION_FAILED);
        }
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /**
     * The table name comes only from REPORTED_GROUPS, a private constant.
     * Nothing a client sends reaches this string.
     */
    private long countRows(String table, Long tenantId) {
        Object result = entityManager
                .createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE tenant_id = :tenantId")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) result).longValue();
    }

    private Tenant currentTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Shop not found"));
    }
}
