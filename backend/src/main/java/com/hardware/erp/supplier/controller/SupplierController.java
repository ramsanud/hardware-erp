package com.hardware.erp.supplier.controller;

import com.hardware.erp.auth.entity.PermissionCode;
import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.ErrorResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.supplier.dto.*;
import com.hardware.erp.supplier.entity.SupplierStatus;
import com.hardware.erp.supplier.service.SupplierService;
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

import java.util.List;
import java.util.Map;

/**
 * Depends On:
 *   Module 1 - SUPPLIER_VIEW and SUPPLIER_MANAGE, seeded by V1.
 */
@RestController
@RequestMapping("/v1/suppliers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "6. Suppliers", description = "Businesses the shop buys from. Module 2.")
public class SupplierController {

    private static final int MAX_PAGE_SIZE = 100;

    /** Whitelist: a raw parameter reaching ORDER BY is an injection surface. */
    private static final Map<String, String> SORTABLE = Map.of(
            "supplierName", "supplierName",
            "supplierCode", "supplierCode",
            "city", "city",
            "paymentTermsDays", "paymentTermsDays",
            "status", "status",
            "createdAt", "createdAt");

    private final SupplierService supplierService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SUPPLIER_VIEW)")
    @Operation(
            summary = "Search suppliers",
            description = "One search box matches name, code, mobile, contact person, "
                        + "GST number or city - counter staff search by whatever they "
                        + "remember. Page size is clamped to 100 and sort fields are "
                        + "whitelisted.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Matching suppliers",
                    content = @Content(examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "data": {
                        "content": [
                          {
                            "id": 7, "supplierCode": "SUP-0007",
                            "supplierName": "Sri Balaji Hardware Agencies",
                            "contactPerson": "Ramesh Kumar", "mobileNo": "9842011223",
                            "city": "Madurai", "gstNo": "33AABCS1429B1ZP",
                            "paymentTermsDays": 30, "creditLimitDisplay": "5,00,000.00",
                            "status": "ACTIVE"
                          }
                        ],
                        "page": 0, "size": 20, "totalElements": 12,
                        "totalPages": 1, "first": true, "last": true
                      },
                      "timestamp": "2026-08-14T09:14:22.331+05:30"
                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Missing SUPPLIER_VIEW",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<PageResponse<SupplierSummaryResponse>>> search(
            @Parameter(description = "Matches name, code, mobile, contact, GST or city",
                       example = "balaji")
            @RequestParam(required = false) String search,
            @Parameter(example = "ACTIVE") @RequestParam(required = false) SupplierStatus status,
            @Parameter(example = "Madurai") @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "supplierName, supplierCode, city, paymentTermsDays, status, createdAt")
            @RequestParam(defaultValue = "supplierName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String safeSort = SORTABLE.getOrDefault(sortBy, "supplierName");
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.DESC : Sort.Direction.ASC;

        var pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(direction, safeSort));
        return ResponseEntity.ok(ApiResponse.ok(
                supplierService.search(search, status, city, pageable)));
    }

    @GetMapping("/cities")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SUPPLIER_VIEW)")
    @Operation(summary = "Distinct cities, for the filter dropdown")
    public ResponseEntity<ApiResponse<List<String>>> cities() {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.cities()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SUPPLIER_VIEW)")
    @Operation(
            summary = "Get one supplier with its contacts",
            description = "The bank account number is masked to its last four digits.")
    public ResponseEntity<ApiResponse<SupplierResponse>> get(
            @Parameter(example = "7") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.get(id)));
    }

    @GetMapping("/{id}/bank-account-number")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SUPPLIER_VIEW_BANK_ACCOUNT)")
    @Operation(
            summary = "Reveal the full (unmasked) bank account number",
            description = "CR-018. Every SUPPLIER_VIEW holder already sees the last 4 "
                        + "digits on the supplier record; this returns the full number, "
                        + "decrypted on demand, and logs a BANK_ACCOUNT_REVEALED security "
                        + "audit event. The decrypted value is never cached or returned "
                        + "from any other endpoint.")
    public ResponseEntity<ApiResponse<String>> revealBankAccountNumber(
            @Parameter(example = "7") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.revealBankAccountNumber(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SUPPLIER_MANAGE)")
    @Operation(
            summary = "Create a supplier",
            description = "Leave `supplierCode` blank and the system generates the "
                        + "next SUP-nnnn. If both a GST number and a state code are "
                        + "given they must agree - the first two digits of a GSTIN "
                        + "are the state code, and a mismatch means purchases would "
                        + "be taxed under the wrong head.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "Created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Duplicate code, name or GST number",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                    {
                      "success": false,
                      "message": "Supplier name 'Sri Balaji Hardware Agencies' is already in use",
                      "code": "DUPLICATE_RESOURCE",
                      "timestamp": "2026-08-14T09:14:22.331+05:30"
                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "GST state code disagrees with the address",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<SupplierResponse>> create(
            @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Supplier created", supplierService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SUPPLIER_MANAGE)")
    @Operation(summary = "Update a supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> update(
            @PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Supplier updated",
                supplierService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SUPPLIER_MANAGE)")
    @Operation(
            summary = "Deactivate a supplier (soft delete)",
            description = "The row is never removed. Purchase orders and payments "
                        + "from Module 8 reference supplier_id permanently.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        supplierService.softDelete(id, SecurityUtils.requireCurrentUser().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/deleted")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SUPPLIER_MANAGE)")
    @Operation(
            summary = "Deleted suppliers, for recovery (CR-058)",
            description = "Soft-deleted rows are hidden from every other endpoint by "
                        + "`@SQLRestriction`, which until now made a mistaken deactivation "
                        + "irreversible from the UI. Gated on SUPPLIER_MANAGE, not "
                        + "SUPPLIER_VIEW: only someone who can restore a record has any "
                        + "reason to see the deleted list. Not paginated - a shop's "
                        + "deleted-supplier list is short by nature.")
    public ResponseEntity<ApiResponse<List<SupplierDeletedResponse>>> listDeleted() {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.listDeleted()));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SUPPLIER_MANAGE)")
    @Operation(
            summary = "Restore a deleted supplier (CR-058)",
            description = "Clears deleted_at/deleted_by and returns the supplier to ACTIVE, "
                        + "in place: the same supplier_id, the same code, the same contacts, "
                        + "and every purchase document that already references it. A supplier "
                        + "that is not deleted, or belongs to another shop, gives 404.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "Restored"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Not deleted, unknown, or another tenant's",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> restore(@Parameter(example = "13") @PathVariable Long id) {
        supplierService.restore(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- contacts ----------------

    @PostMapping("/{id}/contacts")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SUPPLIER_MANAGE)")
    @Operation(
            summary = "Add a contact person",
            description = "Setting `primary` clears the flag on any other contact "
                        + "for this supplier. At most one primary is allowed, "
                        + "enforced by a partial unique index.")
    public ResponseEntity<ApiResponse<SupplierContactResponse>> addContact(
            @PathVariable Long id, @Valid @RequestBody SupplierContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Contact added", supplierService.addContact(id, request)));
    }

    @PutMapping("/{id}/contacts/{contactId}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SUPPLIER_MANAGE)")
    @Operation(summary = "Update a contact person")
    public ResponseEntity<ApiResponse<SupplierContactResponse>> updateContact(
            @PathVariable Long id, @PathVariable Long contactId,
            @Valid @RequestBody SupplierContactRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Contact updated",
                supplierService.updateContact(id, contactId, request)));
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SUPPLIER_MANAGE)")
    @Operation(
            summary = "Remove a contact person",
            description = "A hard delete: contacts carry no financial history.")
    public ResponseEntity<Void> deleteContact(
            @PathVariable Long id, @PathVariable Long contactId) {
        supplierService.deleteContact(id, contactId);
        return ResponseEntity.noContent().build();
    }
}
