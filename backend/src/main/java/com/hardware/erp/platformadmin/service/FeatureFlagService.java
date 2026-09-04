package com.hardware.erp.platformadmin.service;

import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.platformadmin.dto.CreateFeatureFlagRequest;
import com.hardware.erp.platformadmin.dto.FeatureFlagResponse;
import com.hardware.erp.platformadmin.entity.FeatureFlag;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.repository.FeatureFlagRepository;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Phase 8 - Feature Flags. isEnabled(key) is the one real backend
 * enforcement point (spec: "Backend must enforce... do not rely on
 * frontend flags") - any future feature that wants to be flag-gated calls
 * this, never a frontend-only check. Honest limitation: TENANT/PLAN scope
 * is descriptive metadata only in this pass, not an enforced per-tenant
 * override table - see FeatureFlagScope's own javadoc.
 */
@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository repository;
    private final PlatformAdminRepository platformAdminRepository;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public boolean isEnabled(String flagKey) {
        return repository.findByFlagKey(flagKey).map(FeatureFlag::isEnabled).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<FeatureFlagResponse> list() {
        return repository.findAllByOrderByFlagKeyAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public FeatureFlagResponse create(CreateFeatureFlagRequest request, Long actingAdminId, HttpServletRequest httpRequest) {
        if (repository.existsByFlagKey(request.flagKey())) {
            throw new DuplicateResourceException("flagKey", request.flagKey());
        }
        FeatureFlag flag = repository.save(FeatureFlag.builder()
                .flagKey(request.flagKey().trim())
                .name(request.name().trim())
                .description(request.description())
                .scope(request.scope())
                .enabled(false)
                .updatedBy(actingAdminId)
                .build());
        audit(PlatformAuditAction.FEATURE_FLAG_CREATED, flag, actingAdminId, httpRequest);
        return toResponse(flag);
    }

    @Transactional
    public FeatureFlagResponse setEnabled(Long id, boolean enabled, Long actingAdminId, HttpServletRequest request) {
        FeatureFlag flag = require(id);
        flag.setEnabled(enabled);
        flag.setUpdatedBy(actingAdminId);
        repository.save(flag);
        audit(enabled ? PlatformAuditAction.FEATURE_FLAG_ENABLED : PlatformAuditAction.FEATURE_FLAG_DISABLED,
                flag, actingAdminId, request);
        return toResponse(flag);
    }

    @Transactional
    public void delete(Long id, Long actingAdminId, HttpServletRequest request) {
        FeatureFlag flag = require(id);
        repository.delete(flag);
        audit(PlatformAuditAction.FEATURE_FLAG_DELETED, flag, actingAdminId, request);
    }

    private void audit(PlatformAuditAction action, FeatureFlag flag, Long actingAdminId, HttpServletRequest request) {
        PlatformAdmin admin = platformAdminRepository.getReferenceById(actingAdminId);
        auditService.record(action, admin, true, "FEATURE_FLAG", flag.getId(), flag.getFlagKey(), request);
    }

    private FeatureFlag require(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Feature flag", id));
    }

    private FeatureFlagResponse toResponse(FeatureFlag flag) {
        return new FeatureFlagResponse(
                flag.getId(), flag.getFlagKey(), flag.getName(), flag.getDescription(),
                flag.isEnabled(), flag.getScope(), flag.getCreatedAt(), flag.getUpdatedAt());
    }
}
