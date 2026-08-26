package com.hardware.erp.project.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.project.dto.WorkTypeRequest;
import com.hardware.erp.project.dto.WorkTypeResponse;
import com.hardware.erp.project.service.WorkTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Depends On: Module 1 - PROJECT_VIEW/PROJECT_MANAGE, seeded by V18. */
@RestController
@RequestMapping("/v1/work-types")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "13. Projects", description = "Project work types - user-extensible, not a fixed list. Module 8.")
public class WorkTypeController {

    private final WorkTypeService workTypeService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_VIEW)")
    @Operation(summary = "List work types", description = "Every work type this tenant has defined, alphabetical - no pagination, typically a short list.")
    public ResponseEntity<ApiResponse<List<WorkTypeResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(workTypeService.list()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_MANAGE)")
    @Operation(summary = "Add a work type", description = "If the type of work a project needs doesn't exist yet, add it here first.")
    public ResponseEntity<ApiResponse<WorkTypeResponse>> create(@Valid @RequestBody WorkTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Work type added", workTypeService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_MANAGE)")
    @Operation(summary = "Rename a work type")
    public ResponseEntity<ApiResponse<WorkTypeResponse>> update(
            @PathVariable Long id, @Valid @RequestBody WorkTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Work type updated", workTypeService.update(id, request)));
    }
}
