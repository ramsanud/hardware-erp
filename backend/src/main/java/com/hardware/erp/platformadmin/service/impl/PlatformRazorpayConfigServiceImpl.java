package com.hardware.erp.platformadmin.service.impl;

import com.hardware.erp.billing.config.RazorpayProperties;
import com.hardware.erp.platformadmin.dto.RazorpayConfigResponse;
import com.hardware.erp.platformadmin.dto.UpdateRazorpayConfigRequest;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.entity.PlatformRazorpayConfig;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.platformadmin.repository.PlatformRazorpayConfigRepository;
import com.hardware.erp.platformadmin.service.PlatformAuditService;
import com.hardware.erp.platformadmin.service.PlatformRazorpayConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * CR-057 phase 12 - lets a platform admin fill in real Razorpay credentials
 * from the console, no redeploy needed. Never returns a saved secret back
 * to the browser (RazorpayConfigResponse carries only "configured: true").
 * Every save is audited (PLATFORM_SETTING_UPDATED) - the spec's own
 * "production changes require ... an audit record" rule for Platform
 * Settings, and this is the one setting in the whole console that can move
 * real money.
 */
@Service
@RequiredArgsConstructor
public class PlatformRazorpayConfigServiceImpl implements PlatformRazorpayConfigService {

    private final PlatformRazorpayConfigRepository configRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final PlatformAuditService auditService;
    private final RazorpayProperties envProperties;

    @Override
    @Transactional(readOnly = true)
    public RazorpayConfigResponse get() {
        return configRepository.findById(1L)
                .map(this::toResponse)
                .orElseGet(this::emptyResponse);
    }

    @Override
    @Transactional
    public RazorpayConfigResponse update(UpdateRazorpayConfigRequest request, Long adminId, HttpServletRequest httpRequest) {
        PlatformRazorpayConfig row = configRepository.findById(1L)
                .orElseGet(() -> PlatformRazorpayConfig.builder().id(1L).build());

        row.setEnabled(Boolean.TRUE.equals(request.enabled()));
        if (request.keyId() != null) {
            row.setKeyId(blankToNull(request.keyId()));
        }
        // null = leave unchanged; "" = deliberately clear. Never round-tripped from the response, which never carries the secret.
        if (request.keySecret() != null) {
            row.setKeySecret(blankToNull(request.keySecret()));
        }
        if (request.webhookSecret() != null) {
            row.setWebhookSecret(blankToNull(request.webhookSecret()));
        }
        if (request.proPlanAmountPaise() != null) {
            row.setProPlanAmountPaise(request.proPlanAmountPaise());
        }
        if (request.maxPlanAmountPaise() != null) {
            row.setMaxPlanAmountPaise(request.maxPlanAmountPaise());
        }
        row.setUpdatedAt(LocalDateTime.now());
        row.setUpdatedBy(adminId);

        PlatformRazorpayConfig saved = configRepository.save(row);

        PlatformAdmin actingAdmin = platformAdminRepository.getReferenceById(adminId);
        auditService.record(PlatformAuditAction.PLATFORM_SETTING_UPDATED, actingAdmin, true,
                "PLATFORM_RAZORPAY_CONFIG", 1L,
                "enabled=" + saved.isEnabled() + ", keyId=" + (saved.getKeyId() == null ? "(none)" : saved.getKeyId()),
                httpRequest);

        return toResponse(saved);
    }

    private RazorpayConfigResponse toResponse(PlatformRazorpayConfig row) {
        String source = row.active() ? "DATABASE" : envProperties.active() ? "ENVIRONMENT" : "NOT_CONFIGURED";
        return new RazorpayConfigResponse(
                row.isEnabled(), row.getKeyId(),
                row.getKeySecret() != null && !row.getKeySecret().isBlank(),
                row.getWebhookSecret() != null && !row.getWebhookSecret().isBlank(),
                row.getProPlanAmountPaise(), row.getMaxPlanAmountPaise(),
                source, row.getUpdatedAt());
    }

    private RazorpayConfigResponse emptyResponse() {
        String source = envProperties.active() ? "ENVIRONMENT" : "NOT_CONFIGURED";
        return new RazorpayConfigResponse(
                false, null, false, false,
                envProperties.proPlanAmountPaise(), envProperties.maxPlanAmountPaise(),
                source, null);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
