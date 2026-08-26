package com.hardware.erp.auth.controller;

import com.hardware.erp.auth.dto.*;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.auth.service.UserService;
import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.ErrorResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.security.SecurityUtils;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "2. Users", description = "Owner-created employee accounts")
public class UserController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Sort whitelist. A raw parameter reaching ORDER BY is an injection surface,
     * so an unknown key falls back to the default rather than being passed on.
     */
    private static final Map<String, String> SORTABLE = Map.of(
            "fullName", "fullName",
            "mobileNo", "mobileNo",
            "email", "email",
            "employeeCode", "employeeCode",
            "status", "status",
            "createdAt", "createdAt",
            "lastLoginAt", "lastLoginAt");

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).USER_MANAGE)")
    @Operation(
            summary = "Create an employee account",
            description = """
                    There is no public self-registration (CR-008). This is the only
                    way an account comes into existence, and it requires
                    USER_MANAGE.

                    Set `mustChangePassword` so the employee is forced to replace
                    the temporary password at first sign-in.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "User created",
                    content = @Content(schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "message": "User created",
                      "data": {
                        "id": 11, "fullName": "Karthik Raja", "mobileNo": "9843012345",
                        "email": "karthik@sarahardware.in", "employeeCode": "EMP005",
                        "roleId": 4, "roleCode": "STAFF", "roleName": "Staff",
                        "permissions": ["PRODUCT_VIEW","INVOICE_CREATE"],
                        "status": "ACTIVE", "mustChangePassword": true,
                        "createdAt": "2026-08-13T09:30:00.000"
                      },
                      "timestamp": "2026-08-13T09:30:00.000+05:30"
                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Mobile, email or employee code in use",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                    {
                      "success": false,
                      "message": "Mobile number '9843012345' is already in use",
                      "code": "DUPLICATE_RESOURCE",
                      "timestamp": "2026-08-13T09:30:00.000+05:30"
                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Missing USER_MANAGE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<UserResponse>> create(
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User created", userService.create(request)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).USER_VIEW)")
    @Operation(
            summary = "Search users",
            description = "Free-text search across name, mobile, email and employee "
                        + "code. Page size is clamped to 100 and sort fields are "
                        + "whitelisted.")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> search(
            @Parameter(description = "Matches name, mobile, email or employee code",
                       example = "karthik")
            @RequestParam(required = false) String search,

            @Parameter(example = "ACTIVE")
            @RequestParam(required = false) UserStatus status,

            @Parameter(example = "4")
            @RequestParam(required = false) Long roleId,

            @Parameter(example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "One of: fullName, mobileNo, email, employeeCode, "
                                   + "status, createdAt, lastLoginAt", example = "fullName")
            @RequestParam(defaultValue = "fullName") String sortBy,
            @Parameter(example = "asc") @RequestParam(defaultValue = "asc") String sortDir) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        String safeSort = SORTABLE.getOrDefault(sortBy, "fullName");
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.DESC : Sort.Direction.ASC;

        var pageable = PageRequest.of(safePage,
                safeSize == 0 ? DEFAULT_PAGE_SIZE : safeSize,
                Sort.by(direction, safeSort));

        return ResponseEntity.ok(ApiResponse.ok(
                userService.search(search, status, roleId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).USER_VIEW)")
    @Operation(summary = "Get one user")
    public ResponseEntity<ApiResponse<UserResponse>> get(
            @Parameter(example = "11") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.get(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).USER_MANAGE)")
    @Operation(
            summary = "Update a user",
            description = "A role change or a status change signs the user out of "
                        + "every device immediately. The last active owner cannot "
                        + "be moved off the owner role or deactivated.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "Last active owner protected",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                    {
                      "success": false,
                      "message": "This is the last active owner. Assign another owner before changing or removing this account.",
                      "code": "LAST_OWNER_PROTECTED",
                      "timestamp": "2026-08-13T09:35:00.000+05:30"
                    }""")))
    })
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("User updated", userService.update(id, request)));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).USER_MANAGE)")
    @Operation(
            summary = "Set a temporary password for a user",
            description = "Forces a password change at next sign-in and revokes all "
                        + "of that user's sessions.")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id, @Valid @RequestBody ResetUserPasswordRequest request) {
        userService.resetPassword(id, request);
        return ResponseEntity.ok(ApiResponse.message(
                "Password reset. The user must change it at next sign-in."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).USER_MANAGE)")
    @Operation(
            summary = "Deactivate a user (soft delete)",
            description = "The row is never physically removed: ERP documents "
                        + "reference created_by for the life of the business. "
                        + "You cannot delete your own account or the last owner.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.softDelete(id, SecurityUtils.requireCurrentUser().getId());
        return ResponseEntity.noContent().build();
    }
}
