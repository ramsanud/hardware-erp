package com.hardware.erp.tenant.dto;

import java.util.List;

/**
 * What a reset would destroy, counted from the caller's own tenant a moment
 * before they confirm it (CR-067).
 *
 * The project already refuses to let a file import write straight to the
 * database - preview, then confirm, then commit. An irreversible wipe deserves
 * at least the same, so the dialog can say "342 invoices" rather than "your
 * data".
 *
 * @param shopName        the phrase the caller must type back, so the frontend
 *                        never has to guess at it from a separate settings call
 * @param groups          one row per business record type, in the order shown
 * @param totalRecords    sum of every group, for the headline count
 * @param captchaRequired whether a Turnstile token must accompany the reset -
 *                        false on installs that never configured it
 */
public record DataResetPreviewResponse(
        String shopName,
        List<Group> groups,
        long totalRecords,
        boolean captchaRequired
) {
    /** @param label human wording, not a table name - the API never exposes one */
    public record Group(String label, long count) {}
}
