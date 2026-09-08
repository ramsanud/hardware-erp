package com.hardware.erp.tenant.service;

import com.hardware.erp.tenant.dto.DataResetPreviewResponse;
import com.hardware.erp.tenant.dto.DataResetRequest;
import com.hardware.erp.tenant.dto.DataResetResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Erases the calling shop's transactional data and nothing else (CR-067).
 *
 * Always operates on {@code SecurityUtils.requireCurrentTenantId()}. There is
 * no overload that accepts a tenant id, and there must never be one - the
 * whole feature would become a cross-tenant wipe the moment a client could
 * name its target.
 */
public interface DataResetService {

    /** What a reset would remove right now, for the confirmation dialog. */
    DataResetPreviewResponse preview();

    /**
     * Verifies the CAPTCHA and the typed shop name, then deletes.
     *
     * @throws com.hardware.erp.common.exception.BusinessException if either
     *         confirmation fails - nothing is deleted in that case
     */
    DataResetResponse reset(DataResetRequest request, HttpServletRequest httpRequest);
}
