package com.hardware.erp.auth.dto;

import java.util.List;

/** Confirming enrollment logs the user straight in (a real session) and shows the recovery codes exactly once. */
public record MfaConfirmResponse(
        LoginResponse session,
        List<String> backupCodes
) {}
