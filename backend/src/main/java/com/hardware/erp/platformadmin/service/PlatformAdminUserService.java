package com.hardware.erp.platformadmin.service;

import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.platformadmin.dto.CreatePlatformAdminRequest;
import com.hardware.erp.platformadmin.dto.PlatformAdminResponse;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAdminStatus;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.mapper.PlatformAdminMapper;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Create/list only, in Phase 1 - just enough to prove the 7-role RBAC model
 * end to end (only SUPER_ADMIN reaches this via @PreAuthorize on the
 * controller). Deactivate/role-change/self-service profile editing arrive
 * with a later phase, the same way tenant UserService grew beyond its first
 * cut.
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminUserService {

    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditService auditService;
    private final PlatformAdminMapper mapper;

    @Transactional
    public PlatformAdminResponse create(CreatePlatformAdminRequest request, HttpServletRequest httpRequest) {
        String email = request.email().trim().toLowerCase();
        if (platformAdminRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new DuplicateResourceException("email", email);
        }

        PlatformAdmin created = PlatformAdmin.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .status(PlatformAdminStatus.ACTIVE)
                .mfaEnabled(false)
                .tokenVersion(0)
                .failedLoginAttempts(0)
                .build();

        PlatformAdmin saved = platformAdminRepository.save(created);

        auditService.record(PlatformAuditAction.PLATFORM_ADMIN_CREATED, saved, true,
                "PLATFORM_ADMIN", saved.getId(), "Created with role " + saved.getRole(), httpRequest);

        return mapper.toResponse(saved);
    }

    public List<PlatformAdminResponse> list() {
        return platformAdminRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(mapper::toResponse)
                .toList();
    }
}
