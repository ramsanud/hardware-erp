package com.hardware.erp.auth.controller;

import com.hardware.erp.auth.dto.RoleRequest;
import com.hardware.erp.auth.dto.RoleResponse;
import com.hardware.erp.auth.service.RoleService;
import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/roles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "3. Roles", description = "Permission sets. Authorization is permission-based, never role-name-based.")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).ROLE_VIEW)")
    @Operation(summary = "List all roles with their permissions and user counts")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Roles",
            content = @Content(examples = @ExampleObject(value = """
            {
              "success": true,
              "data": [
                {
                  "id": 4, "code": "STAFF", "name": "Staff",
                  "description": "Billing counter, no cost visibility",
                  "systemRole": true, "status": "ACTIVE",
                  "permissions": ["PRODUCT_VIEW","INVOICE_CREATE","PAYMENT_VIEW"],
                  "userCount": 3
                }
              ],
              "timestamp": "2026-08-13T09:40:00.000+05:30"
            }"""))))
    public ResponseEntity<ApiResponse<List<RoleResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.findAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).ROLE_VIEW)")
    @Operation(summary = "Get one role")
    public ResponseEntity<ApiResponse<RoleResponse>> get(
            @Parameter(example = "4") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).ROLE_MANAGE)")
    @Operation(
            summary = "Create a custom role",
            description = "Permission codes are validated against the permission "
                        + "table, so codes added by a later module's migration work "
                        + "immediately without a code change.")
    public ResponseEntity<ApiResponse<RoleResponse>> create(
            @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Role created", roleService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).ROLE_MANAGE)")
    @Operation(
            summary = "Update a role",
            description = """
                    Changing the permission set signs out every holder of the role,
                    so a revoked permission takes effect immediately.

                    System roles keep their code. The OWNER role must retain every
                    permission and cannot be deactivated - otherwise the shop could
                    lock itself out of its own administration.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "Business rule violated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                    {
                      "success": false,
                      "message": "The OWNER role must keep every permission. Removing one would leave the shop unable to administer itself.",
                      "code": "BUSINESS_RULE_VIOLATION",
                      "timestamp": "2026-08-13T09:45:00.000+05:30"
                    }""")))
    })
    public ResponseEntity<ApiResponse<RoleResponse>> update(
            @PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Role updated", roleService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).ROLE_MANAGE)")
    @Operation(
            summary = "Delete a custom role",
            description = "System roles cannot be deleted, and a role still held by "
                        + "any user cannot be deleted.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
