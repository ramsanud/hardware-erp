package com.hardware.erp.auth.controller;

import com.hardware.erp.auth.dto.PermissionGroupResponse;
import com.hardware.erp.auth.dto.PermissionResponse;
import com.hardware.erp.auth.service.PermissionService;
import com.hardware.erp.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/permissions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "4. Permissions", description = "Read-only catalogue. Modules add permissions through their own Flyway migration.")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).ROLE_VIEW)")
    @Operation(summary = "Every permission the system knows about")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(permissionService.findAll()));
    }

    @GetMapping("/grouped")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).ROLE_VIEW)")
    @Operation(
            summary = "Permissions grouped by module",
            description = "Drives the section-per-module permission picker on the role screen.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Grouped permissions",
            content = @Content(examples = @ExampleObject(value = """
            {
              "success": true,
              "data": [
                {
                  "moduleCode": "PRODUCT",
                  "permissions": [
                    {"id": 10, "code": "PRODUCT_VIEW", "name": "View products",
                     "description": "Search products and see selling price",
                     "moduleCode": "PRODUCT", "displayOrder": 10},
                    {"id": 12, "code": "PRODUCT_VIEW_COST", "name": "View product cost",
                     "description": "See purchase cost and margin",
                     "moduleCode": "PRODUCT", "displayOrder": 30}
                  ]
                }
              ],
              "timestamp": "2026-08-13T09:50:00.000+05:30"
            }"""))))
    public ResponseEntity<ApiResponse<List<PermissionGroupResponse>>> grouped() {
        return ResponseEntity.ok(ApiResponse.ok(permissionService.findAllGroupedByModule()));
    }
}
