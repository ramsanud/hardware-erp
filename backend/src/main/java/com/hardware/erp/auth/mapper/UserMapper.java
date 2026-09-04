package com.hardware.erp.auth.mapper;

import com.hardware.erp.auth.dto.*;
import com.hardware.erp.auth.entity.Permission;
import com.hardware.erp.auth.entity.RefreshToken;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.User;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One mapper for the whole auth module. Splitting it into UserMapper /
 * RoleMapper / PermissionMapper would create three classes that all need the
 * same imports and invite the alias-service drift the registry forbids.
 */
@Component
public class UserMapper {

    public UserResponse toUserResponse(User user) {
        Role role = user.getRole();
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getMobileNo(),
                user.getEmail(),
                user.getEmployeeCode(),
                role.getId(),
                role.getCode(),
                role.getName(),
                role.permissionCodes(),
                user.getStatus(),
                user.isMustChangePassword(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }

    /** CR-058 recycle bin. Identification and the deletion date only - never security state. */
    public UserDeletedResponse toDeletedResponse(User user) {
        Role role = user.getRole();
        return new UserDeletedResponse(
                user.getId(),
                user.getFullName(),
                user.getMobileNo(),
                user.getEmployeeCode(),
                role == null ? null : role.getName(),
                user.getDeletedAt());
    }

    public RoleResponse toRoleResponse(Role role, long userCount) {
        return new RoleResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isSystemRole(),
                role.getStatus(),
                role.permissionCodes(),
                userCount);
    }

    public PermissionResponse toPermissionResponse(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getDescription(),
                permission.getModuleCode(),
                permission.getDisplayOrder());
    }

    /** Preserves module order from the query rather than re-sorting arbitrarily. */
    public List<PermissionGroupResponse> toPermissionGroups(List<Permission> permissions) {
        Map<String, List<PermissionResponse>> grouped = permissions.stream()
                .sorted(Comparator.comparing(Permission::getModuleCode)
                        .thenComparing(Permission::getDisplayOrder))
                .collect(Collectors.groupingBy(
                        Permission::getModuleCode,
                        java.util.LinkedHashMap::new,
                        Collectors.mapping(this::toPermissionResponse, Collectors.toList())));

        return grouped.entrySet().stream()
                .map(e -> new PermissionGroupResponse(e.getKey(), e.getValue()))
                .toList();
    }

    public SessionResponse toSessionResponse(RefreshToken token, boolean current) {
        return new SessionResponse(
                token.getId(),
                token.getIpAddress(),
                token.getUserAgent(),
                token.getCreatedAt(),
                token.getLastUsedAt(),
                token.getExpiresAt(),
                current);
    }
}
