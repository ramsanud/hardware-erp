package com.hardware.erp.auth.service.impl;

import com.hardware.erp.auth.dto.*;
import com.hardware.erp.auth.entity.*;
import com.hardware.erp.auth.mapper.UserMapper;
import com.hardware.erp.auth.repository.RefreshTokenRepository;
import com.hardware.erp.auth.repository.RoleRepository;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.auth.service.SecurityAuditService;
import com.hardware.erp.auth.service.UserService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.service.EntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String OWNER_CODE = "OWNER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final SecurityAuditService auditService;
    private final EntitlementService entitlementService;
    private final com.hardware.erp.common.activity.ActivityLogRepository activityLogRepository;

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        String email = normaliseEmail(request.email());
        String employeeCode = blankToNull(request.employeeCode());

        // mobile_no/email are checked globally, not per tenant - login has no
        // tenant selector, so an identifier must resolve to one user
        // platform-wide (CR-016).
        if (userRepository.existsByMobileNo(request.mobileNo())) {
            throw new DuplicateResourceException("Mobile number", request.mobileNo());
        }
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email", email);
        }
        if (employeeCode != null
                && userRepository.existsByEmployeeCodeAndTenantId(employeeCode, tenantId)) {
            throw new DuplicateResourceException("Employee code", employeeCode);
        }

        Role role = requireRole(request.roleId(), tenantId);
        if (!role.isActive()) {
            throw new BusinessException("Cannot assign an inactive role");
        }
        if (OWNER_CODE.equals(role.getCode())) {
            entitlementService.requireCanAddOwner();
        }

        User user = User.builder()
                .tenant(role.getTenant())
                .role(role)
                .fullName(request.fullName().trim())
                .mobileNo(request.mobileNo().trim())
                .email(email)
                .employeeCode(employeeCode)
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .mustChangePassword(request.mustChangePassword())
                .tokenVersion(0)
                .failedLoginAttempts(0)
                .passwordChangedAt(LocalDateTime.now())
                .build();

        User saved = userRepository.save(user);
        auditService.success(AuditAction.USER_CREATED, saved.getId(), saved.getFullName(),
                "USER", saved.getId());
        return userMapper.toUserResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        User user = requireUser(id, tenantId);
        String email = normaliseEmail(request.email());
        String employeeCode = blankToNull(request.employeeCode());

        if (userRepository.existsByMobileNoAndIdNot(request.mobileNo(), id)) {
            throw new DuplicateResourceException("Mobile number", request.mobileNo());
        }
        if (email != null && userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new DuplicateResourceException("Email", email);
        }
        if (employeeCode != null
                && userRepository.existsByEmployeeCodeAndTenantIdAndIdNot(employeeCode, tenantId, id)) {
            throw new DuplicateResourceException("Employee code", employeeCode);
        }

        Role newRole = requireRole(request.roleId(), tenantId);
        boolean roleChanged = !newRole.getId().equals(user.getRole().getId());
        boolean losingActive = request.status() != UserStatus.ACTIVE
                && user.getStatus() == UserStatus.ACTIVE;

        if (roleChanged && !newRole.isActive()) {
            throw new BusinessException("Cannot assign an inactive role");
        }

        // Either change could remove the last owner.
        if (roleChanged || losingActive) {
            guardLastActiveOwner(user, tenantId);
        }

        user.setFullName(request.fullName().trim());
        user.setMobileNo(request.mobileNo().trim());
        user.setEmail(email);
        user.setEmployeeCode(employeeCode);
        user.setRole(newRole);
        user.setStatus(request.status());

        // A role change alters permissions; a deactivation must bite now, not at
        // token expiry. Both require every issued token to stop working.
        if (roleChanged || request.status() != UserStatus.ACTIVE) {
            user.invalidateAllTokens();
            refreshTokenRepository.revokeAllForUser(id,
                    roleChanged ? RevokedReason.ROLE_CHANGED : RevokedReason.USER_DEACTIVATED,
                    LocalDateTime.now());
        }

        User saved = userRepository.save(user);
        auditService.success(
                roleChanged ? AuditAction.ROLE_CHANGED : AuditAction.USER_UPDATED,
                id, saved.getFullName(), "USER", id);
        return userMapper.toUserResponse(saved);
    }

    /**
     * Self-service edit. Takes the id from the security context via the
     * controller, never from the request body, so a user cannot edit someone else.
     */
    @Override
    @Transactional
    public UserResponse updateOwnProfile(Long userId, UpdateProfileRequest request) {
        User user = requireUser(userId, SecurityUtils.requireCurrentTenantId());
        String email = normaliseEmail(request.email());

        if (email != null && userRepository.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
            throw new DuplicateResourceException("Email", email);
        }

        user.setFullName(request.fullName().trim());
        user.setEmail(email);

        User saved = userRepository.save(user);
        auditService.success(AuditAction.USER_UPDATED, userId, saved.getFullName(),
                "USER", userId);
        return userMapper.toUserResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return userMapper.toUserResponse(requireUser(id, SecurityUtils.requireCurrentTenantId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String search, UserStatus status,
                                             Long roleId, Pageable pageable) {
        return PageResponse.from(
                userRepository.search(SecurityUtils.requireCurrentTenantId(),
                        blankToNull(search), status, roleId, pageable),
                userMapper::toUserResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<com.hardware.erp.auth.dto.UserActivityResponse> activity(Long id, Pageable pageable) {
        // requireUser confirms id belongs to the caller's own tenant BEFORE
        // it is ever used to filter activity_log, which carries no
        // tenant_id of its own - see ActivityLogRepository's javadoc.
        User user = requireUser(id, SecurityUtils.requireCurrentTenantId());
        return PageResponse.from(
                activityLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable),
                log -> new com.hardware.erp.auth.dto.UserActivityResponse(
                        log.getId(), log.getModuleCode(), log.getEntityType(), log.getEntityId(),
                        log.getEntityLabel(), log.getAction().name(), log.getRemarks(), log.getCreatedAt()));
    }

    @Override
    @Transactional
    public void resetPassword(Long id, ResetUserPasswordRequest request) {
        User user = requireUser(id, SecurityUtils.requireCurrentTenantId());

        // Owner-set passwords are temporary by definition. applyNewPassword also
        // bumps tokenVersion, so the user is signed out everywhere at once.
        user.applyNewPassword(passwordEncoder.encode(request.newPassword()), true);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(id, RevokedReason.PASSWORD_RESET,
                LocalDateTime.now());

        auditService.success(AuditAction.PASSWORD_RESET_BY_ADMIN, id, user.getFullName(),
                "USER", id);
    }

    @Override
    @Transactional
    public void softDelete(Long id, Long actingUserId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        User user = requireUser(id, tenantId);

        if (id.equals(actingUserId)) {
            throw new BusinessException("You cannot delete your own account");
        }
        guardLastActiveOwner(user, tenantId);

        // Soft delete only. Every ERP document from Module 2 onwards carries
        // created_by, and those references must resolve to a name for years.
        user.softDelete(actingUserId);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(id, RevokedReason.USER_DEACTIVATED,
                LocalDateTime.now());

        auditService.success(AuditAction.USER_DEACTIVATED, id, user.getFullName(),
                "USER", id);
    }

    /**
     * The shop must never be left without a working owner account.
     *
     * The owner list is read under a pessimistic write lock, scoped to one
     * tenant. Without either the lock or the tenant scope, two admins
     * deactivating the two remaining owners of the same shop simultaneously
     * would each read a count of two, each pass this check, and lock the
     * shop out with no way back in - or worse, one shop's owner count would
     * be inflated by every other shop's owners (CR-016).
     */
    private void guardLastActiveOwner(User user, Long tenantId) {
        if (!user.isOwner() || user.getStatus() != UserStatus.ACTIVE) {
            return;
        }
        List<User> activeOwners = userRepository.lockActiveOwners(tenantId);
        if (activeOwners.size() <= 1) {
            throw new BusinessException(
                    "This is the last active owner. Assign another owner before changing "
                    + "or removing this account.",
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "LAST_OWNER_PROTECTED");
        }
    }

    /**
     * Scoped by tenant, not a plain findById - a user id alone does not
     * prove the row belongs to the caller's shop (CR-016). A cross-tenant id
     * is treated as not found, exactly like SupplierServiceImpl.requireContact
     * treats a foreign parent, so it does not confirm the row's existence to
     * an unauthorized caller.
     */
    private User requireUser(Long id, Long tenantId) {
        return userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private Role requireRole(Long id, Long tenantId) {
        return roleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));
    }

    private String normaliseEmail(String email) {
        String trimmed = blankToNull(email);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
