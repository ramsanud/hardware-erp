package com.hardware.erp.labour.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.labour.dto.WorkerRequest;
import com.hardware.erp.labour.dto.WorkerResponse;
import com.hardware.erp.labour.entity.WorkerStatus;
import com.hardware.erp.labour.service.WorkerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Worker directory (CR-036 phase 4) - a shop's own day-wage labour force, separate from Supplier/Customer. */
@RestController
@RequestMapping("/v1/workers")
@RequiredArgsConstructor
@Tag(name = "Labour")
public class WorkerController {

    private final WorkerService workerService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).LABOUR_MANAGE)")
    public ApiResponse<WorkerResponse> create(@Valid @RequestBody WorkerRequest request) {
        return ApiResponse.ok(workerService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).LABOUR_VIEW)")
    public ApiResponse<PageResponse<WorkerResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) WorkerStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(workerService.search(search, status, pageable));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).LABOUR_VIEW)")
    public ApiResponse<List<WorkerResponse>> listActive() {
        return ApiResponse.ok(workerService.listActive());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).LABOUR_VIEW)")
    public ApiResponse<WorkerResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(workerService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).LABOUR_MANAGE)")
    public ApiResponse<WorkerResponse> update(@PathVariable Long id, @Valid @RequestBody WorkerRequest request) {
        return ApiResponse.ok(workerService.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).LABOUR_MANAGE)")
    public void deactivate(@PathVariable Long id) {
        workerService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).LABOUR_MANAGE)")
    public void activate(@PathVariable Long id) {
        workerService.activate(id);
    }
}
