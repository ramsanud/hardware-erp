package com.hardware.erp.labour.dto;

import com.hardware.erp.labour.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttendanceEntryRequest(
        @NotNull Long workerId,
        @NotNull AttendanceStatus status,
        Long projectId,
        @Size(max = 500) String notes
) {}
