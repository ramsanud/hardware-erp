package com.hardware.erp.labour.dto;

import com.hardware.erp.labour.entity.AttendanceStatus;

import java.time.LocalDate;

public record WorkerAttendanceResponse(
        Long id,
        Long workerId,
        String workerName,
        LocalDate attendanceDate,
        AttendanceStatus status,
        Long projectId,
        String projectName,
        String notes,
        long wagePaise,
        String wageDisplay
) {}
