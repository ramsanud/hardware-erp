package com.hardware.erp.auth.dto;

import com.hardware.erp.auth.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Never carries passwordHash, tokenVersion, failedLoginAttempts or lockedUntil.
 * Those are internal security state; exposing them tells an attacker how close
 * they are to a lockout.
 */
@Schema(name = "UserResponse")
public record UserResponse(

        @Schema(example = "4") Long id,
        @Schema(example = "Karthik Raja") String fullName,
        @Schema(example = "9843012345") String mobileNo,
        @Schema(example = "karthik@sarahardware.in") String email,
        @Schema(example = "EMP005") String employeeCode,
        @Schema(example = "4") Long roleId,
        @Schema(example = "STAFF") String roleCode,
        @Schema(example = "Staff") String roleName,
        @Schema(description = "Effective permissions, used by the UI to hide actions the server would reject anyway")
        Set<String> permissions,
        @Schema(example = "ACTIVE") UserStatus status,
        @Schema(example = "false") boolean mustChangePassword,
        @Schema(example = "2026-08-13T09:14:22.331") LocalDateTime lastLoginAt,
        @Schema(example = "2026-07-02T11:00:00.000") LocalDateTime createdAt
) {}
