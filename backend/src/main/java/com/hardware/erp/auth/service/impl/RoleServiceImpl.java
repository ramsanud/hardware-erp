package com.hardware.erp.auth.service.impl;

import com.hardware.erp.auth.dto.RoleRequest;
import com.hardware.erp.auth.dto.RoleResponse;
import com.hardware.erp.auth.entity.*;
import com.hardware.erp.auth.mapper.UserMapper;
import com.hardware.erp.auth.repository.PermissionRepository;
import com.hardware.erp.auth.repository.RefreshTokenRepository;
import com.hardware.erp.auth.repository.RoleRepository;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.auth.service.RoleService;
import com.hardware.erp.auth.service.SecurityAuditService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private static final String OWNER_CODE = "OWNER";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantRepository tenantRepository;
    private final UserMapper userMapper;
    private final SecurityAuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return roleRepository.findAllWithPermissions(SecurityUtils.requireCurrentTenantId()).stream()
                .map(role -> userMapper.toRoleResponse(
                        role, userRepository.countByRoleId(role.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse get(Long id) {
        Role role = requireRole(id, SecurityUtils.requireCurrentTenantId());
        return userMapper.toRoleResponse(role, userRepository.countByRoleId(id));
    }

    @Override
    @Transactional
    public RoleResponse create(RoleRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        if (roleRepository.existsByCodeAndTenantId(request.code(), tenantId)) {
            throw new DuplicateResourceException("Role code", request.code());
        }
        if (roleRepository.existsByNameIgnoreCaseAndTenantId(request.name().trim(), tenantId)) {
            throw new DuplicateResourceException("Role name", request.name().trim());
        }

        Role role = Role.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .code(request.code())
                .name(request.name().trim())
                .description(request.description())
                .systemRole(false)
                .status(request.status())
                .permissions(resolvePermissions(request.permissions()))
                .build();

        Role saved = roleRepository.save(role);
        auditService.success(AuditAction.ROLE_CREATED, null, null, "ROLE", saved.getId());
        return userMapper.toRoleResponse(saved, 0);
    }

    @Override
    @Transactional
    public RoleResponse update(Long id, RoleRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Role role = requireRole(id, tenantId);
        Set<Permission> permissions = resolvePermissions(request.permissions());

        if (role.isSystemRole()) {
            // Code and name are referenced by seed data, tests and the frontend
            // permission map. Permissions and description stay editable.
            if (!role.getCode().equals(request.code())) {
                throw new BusinessException(
                        "The code of a system role cannot be changed");
            }
            if (OWNER_CODE.equals(role.getCode())) {
                long allPermissions = permissionRepository.count();
                if (permissions.size() < allPermissions) {
                    throw new BusinessException(
                            "The OWNER role must keep every permission. Removing one would "
                            + "leave the shop unable to administer itself.");
                }
                if (request.status() != RoleStatus.ACTIVE) {
                    throw new BusinessException("The OWNER role cannot be deactivated");
                }
            }
        } else {
            if (roleRepository.existsByCodeAndTenantIdAndIdNot(request.code(), tenantId, id)) {
                throw new DuplicateResourceException("Role code", request.code());
            }
            if (roleRepository.existsByNameIgnoreCaseAndTenantIdAndIdNot(
                    request.name().trim(), tenantId, id)) {
                throw new DuplicateResourceException("Role name", request.name().trim());
            }
            role.setCode(request.code());
        }

        boolean permissionsChanged = !role.getPermissions().equals(permissions);
        boolean deactivating = request.status() != RoleStatus.ACTIVE
                && role.getStatus() == RoleStatus.ACTIVE;

        if (deactivating && userRepository.countByRoleId(id) > 0) {
            throw new BusinessException(
                    "Users still hold this role. Reassign them before deactivating it.");
        }

        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setStatus(request.status());
        role.setPermissions(permissions);
        Role saved = roleRepository.save(role);

        // A permission change alters what every holder of this role may do.
        // Their outstanding access tokens must stop working immediately rather
        // than at expiry, so every holder's token version is bumped.
        if (permissionsChanged || deactivating) {
            invalidateSessionsForRole(id);
        }

        auditService.success(AuditAction.ROLE_UPDATED, null, null, "ROLE", id);
        return userMapper.toRoleResponse(saved, userRepository.countByRoleId(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Role role = requireRole(id, SecurityUtils.requireCurrentTenantId());

        if (role.isSystemRole()) {
            throw new BusinessException("System roles cannot be deleted");
        }
        long assigned = userRepository.countByRoleId(id);
        if (assigned > 0) {
            throw new BusinessException(
                    "%d user(s) still hold this role. Reassign them first.".formatted(assigned));
        }

        roleRepository.delete(role);
        auditService.success(AuditAction.ROLE_DELETED, null, null, "ROLE", id);
    }

    private void invalidateSessionsForRole(Long roleId) {
        List<User> holders = userRepository.findAllByRoleId(roleId);
        LocalDateTime now = LocalDateTime.now();
        for (User user : holders) {
            user.invalidateAllTokens();
            refreshTokenRepository.revokeAllForUser(
                    user.getId(), RevokedReason.ROLE_CHANGED, now);
        }
        userRepository.saveAll(holders);
    }

    /**
     * Resolves codes against the database rather than a Java constant list, so
     * permissions added by a future module's migration are usable immediately.
     */
    private Set<Permission> resolvePermissions(Set<String> codes) {
        Set<Permission> found = permissionRepository.findByCodeIn(codes);
        if (found.size() != codes.size()) {
            Set<String> known = found.stream().map(Permission::getCode)
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> unknown = new LinkedHashSet<>(codes);
            unknown.removeAll(known);
            throw new BusinessException("Unknown permission(s): " + String.join(", ", unknown));
        }
        return new LinkedHashSet<>(found);
    }

    /**
     * Scoped by tenant, not a plain findById - a role id alone does not
     * prove it belongs to the caller's shop (CR-016).
     */
    private Role requireRole(Long id, Long tenantId) {
        return roleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));
    }
}
