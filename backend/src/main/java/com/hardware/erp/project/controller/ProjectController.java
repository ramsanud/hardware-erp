package com.hardware.erp.project.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.project.dto.*;
import com.hardware.erp.project.entity.ProjectStatus;
import com.hardware.erp.project.service.ProjectExpenseService;
import com.hardware.erp.project.service.ProjectMaterialService;
import com.hardware.erp.project.service.ProjectPaymentService;
import com.hardware.erp.project.service.ProjectService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Depends On:
 *   Customer Module - every project belongs to a customer
 *   Product Module - project materials reference existing products
 *   Supplier Module - project materials may optionally reference a supplier
 */
@RestController
@RequestMapping("/v1/projects")
@RequiredArgsConstructor
@Tag(name = "13. Projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMaterialService materialService;
    private final ProjectExpenseService expenseService;
    private final ProjectPaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_MANAGE)")
    public ApiResponse<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        return ApiResponse.ok(projectService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_VIEW)")
    public ApiResponse<PageResponse<ProjectSummaryResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) Long customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(projectService.search(search, status, customerId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_VIEW)")
    public ApiResponse<ProjectResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(projectService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_MANAGE)")
    public ApiResponse<ProjectResponse> update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return ApiResponse.ok(projectService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_MANAGE)")
    public ApiResponse<ProjectResponse> changeStatus(
            @PathVariable Long id, @Valid @RequestBody ProjectStatusChangeRequest request) {
        return ApiResponse.ok(projectService.changeStatus(id, request));
    }

    // ---------------- materials ----------------

    @GetMapping("/{id}/materials")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_MATERIAL_VIEW)")
    public ApiResponse<List<ProjectMaterialResponse>> materials(@PathVariable Long id) {
        return ApiResponse.ok(materialService.list(id));
    }

    @PostMapping("/{id}/materials")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_MATERIAL_MANAGE)")
    public ApiResponse<ProjectMaterialResponse> addMaterial(
            @PathVariable Long id, @Valid @RequestBody ProjectMaterialRequest request) {
        return ApiResponse.ok(materialService.add(id, request));
    }

    @PutMapping("/{id}/materials/{materialId}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_MATERIAL_MANAGE)")
    public ApiResponse<ProjectMaterialResponse> updateMaterial(
            @PathVariable Long id, @PathVariable Long materialId, @Valid @RequestBody ProjectMaterialRequest request) {
        return ApiResponse.ok(materialService.update(id, materialId, request));
    }

    @DeleteMapping("/{id}/materials/{materialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_MATERIAL_MANAGE)")
    public void removeMaterial(@PathVariable Long id, @PathVariable Long materialId) {
        materialService.remove(id, materialId);
    }

    // ---------------- expenses ----------------

    @GetMapping("/{id}/expenses")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_VIEW)")
    public ApiResponse<List<ProjectExpenseResponse>> expenses(@PathVariable Long id) {
        return ApiResponse.ok(expenseService.list(id));
    }

    @PostMapping("/{id}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_MANAGE)")
    public ApiResponse<ProjectExpenseResponse> addExpense(
            @PathVariable Long id, @Valid @RequestBody ProjectExpenseRequest request) {
        return ApiResponse.ok(expenseService.add(id, request));
    }

    @DeleteMapping("/{id}/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_MANAGE)")
    public void removeExpense(@PathVariable Long id, @PathVariable Long expenseId) {
        expenseService.remove(id, expenseId);
    }

    // ---------------- payments ----------------

    @GetMapping("/{id}/payments")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_VIEW)")
    public ApiResponse<List<ProjectPaymentResponse>> payments(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.list(id));
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_MANAGE)")
    public ApiResponse<ProjectPaymentResponse> addPayment(
            @PathVariable Long id, @Valid @RequestBody ProjectPaymentRequest request) {
        return ApiResponse.ok(paymentService.add(id, request));
    }
}
