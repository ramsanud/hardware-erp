package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * CR-058. The recycle-bin projection - see SupplierDeletedResponse.
 *
 * Like UserResponse it never carries passwordHash, tokenVersion,
 * failedLoginAttempts or lockedUntil: internal security state stays internal
 * whether the account is live or deleted.
 */
@Schema(name = "UserDeletedResponse")
public record UserDeletedResponse(

        @Schema(example = "12") Long id,
        @Schema(example = "Former Employee") String fullName,
        @Schema(example = "9843089012") String mobileNo,
        @Schema(example = "EMP012") String employeeCode,
        @Schema(example = "Staff") String roleName,
        @Schema(example = "2026-08-30T11:04:00.000") LocalDateTime deletedAt
) {}
