package com.hardware.erp.labour.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.labour.dto.WorkerPaymentRequest;
import com.hardware.erp.labour.dto.WorkerPaymentResponse;
import com.hardware.erp.labour.dto.WorkerWageSummaryResponse;
import com.hardware.erp.labour.service.WorkerPaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Labour")
public class WorkerPaymentController {

    private final WorkerPaymentService paymentService;

    @PostMapping("/worker-payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).LABOUR_MANAGE)")
    public ApiResponse<WorkerPaymentResponse> create(@Valid @RequestBody WorkerPaymentRequest request) {
        return ApiResponse.ok(paymentService.create(request));
    }

    @PostMapping("/worker-payments/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).LABOUR_MANAGE)")
    public void cancel(@PathVariable Long id) {
        paymentService.cancel(id);
    }

    @GetMapping("/workers/{workerId}/payments")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).LABOUR_VIEW)")
    public ApiResponse<List<WorkerPaymentResponse>> listForWorker(@PathVariable Long workerId) {
        return ApiResponse.ok(paymentService.listForWorker(workerId));
    }

    @GetMapping("/workers/{workerId}/wage-summary")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).LABOUR_VIEW)")
    public ApiResponse<WorkerWageSummaryResponse> wageSummary(
            @PathVariable Long workerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.ok(paymentService.wageSummary(workerId, fromDate, toDate));
    }
}
