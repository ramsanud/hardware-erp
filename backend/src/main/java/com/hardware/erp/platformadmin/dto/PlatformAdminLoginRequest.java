package com.hardware.erp.platformadmin.dto;

import jakarta.validation.constraints.NotBlank;

public record PlatformAdminLoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {}
