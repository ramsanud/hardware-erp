package com.hardware.erp.platformadmin.dto;

public record DatabaseDiagnosticsResponse(
        boolean connectionReachable,
        Long pingMs,
        /** Null when the DataSource is not a HikariDataSource (e.g. under a test profile using a different pool). */
        PoolStatus pool,
        String migrationVersion,
        int appliedMigrationCount,
        boolean migrationsPending
) {
    public record PoolStatus(int active, int idle, int total, int maxSize) {}
}
