package com.hardware.erp.platformadmin.dto;

import com.hardware.erp.platformadmin.entity.PlatformAdminRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePlatformAdminRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 255) String email,

        /** A one-time bootstrap password only - mustChangePassword semantics arrive with a later phase. */
        @NotBlank @Size(min = 12, max = 100) String password,

        @NotNull PlatformAdminRole role
) {}
