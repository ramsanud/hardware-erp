package com.hardware.erp.labour.entity;

public enum AttendanceStatus {
    PRESENT,
    ABSENT,
    HALF_DAY;

    /** Wage earned for one day at this attendance status, given the worker's daily rate. */
    public long wagePaiseFor(long dailyRatePaise) {
        return switch (this) {
            case PRESENT -> dailyRatePaise;
            case HALF_DAY -> dailyRatePaise / 2;
            case ABSENT -> 0L;
        };
    }
}
