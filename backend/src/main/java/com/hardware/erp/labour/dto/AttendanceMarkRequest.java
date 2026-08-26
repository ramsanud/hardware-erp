package com.hardware.erp.labour.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.util.List;

/** Marks attendance for one or more workers on a single day in one call - a supervisor marks the whole crew at once, not one row at a time. */
public record AttendanceMarkRequest(
        // Attendance records work that has already happened. A future date would
        // silently create wages owed for days nobody has worked yet, and those
        // wages feed straight into the wage summary and a project's labour cost.
        @NotNull @PastOrPresent LocalDate attendanceDate,
        @NotEmpty @Valid List<AttendanceEntryRequest> entries
) {}
