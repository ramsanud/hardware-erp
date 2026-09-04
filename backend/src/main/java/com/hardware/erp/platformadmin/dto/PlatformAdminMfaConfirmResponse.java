package com.hardware.erp.platformadmin.dto;

import java.util.List;

/** Confirming enrollment logs the admin straight in (a real session) and shows the recovery codes exactly once. */
public record PlatformAdminMfaConfirmResponse(
        PlatformAdminSessionResponse session,
        List<String> backupCodes
) {}
