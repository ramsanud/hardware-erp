package com.hardware.erp.labour.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.labour.dto.AttendanceMarkRequest;
import com.hardware.erp.labour.dto.WorkerAttendanceResponse;
import com.hardware.erp.labour.service.AttendanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/attendance")
@RequiredArgsConstructor
@Tag(name = "Labour")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).LABOUR_MANAGE)")
    public ApiResponse<List<WorkerAttendanceResponse>> mark(@Valid @RequestBody AttendanceMarkRequest request) {
        return ApiResponse.ok(attendanceService.mark(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).LABOUR_VIEW)")
    public ApiResponse<List<WorkerAttendanceResponse>> forDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(attendanceService.forDate(date));
    }

    @GetMapping("/worker/{workerId}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).LABOUR_VIEW)")
    public ApiResponse<List<WorkerAttendanceResponse>> historyForWorker(
            @PathVariable Long workerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.ok(attendanceService.historyForWorker(workerId, fromDate, toDate));
    }
}
