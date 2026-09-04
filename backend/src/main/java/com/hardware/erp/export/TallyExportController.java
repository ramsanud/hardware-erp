package com.hardware.erp.export;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * CR-053 backlog item 2. REPORT_FINANCIAL - same gate as the (not yet
 * built) financial reports in Sidebar.tsx's Accounting section - exporting
 * every sale and purchase to an external ledger is an accounting-grade
 * action, not something a shop-floor MANAGER role should be able to do
 * (see V1__auth_schema.sql: MANAGER holds REPORT_VIEW but not
 * REPORT_FINANCIAL, ACCOUNTANT and OWNER hold both).
 */
@RestController
@RequestMapping("/v1/exports/tally")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Exports", description = "CR-053 backlog item 2 - Tally-compatible XML export")
public class TallyExportController {

    private final TallyExportService tallyExportService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).REPORT_FINANCIAL)")
    @Operation(
            summary = "Export sales, purchases, parties and stock items to Tally XML",
            description = "Ledger-level accounting vouchers (party + sales/purchase account + tax "
                        + "ledgers), not item-wise inventory vouchers - see TallyXmlBuilder's own "
                        + "javadoc for the exact scope. Cancelled invoices/purchases are excluded.")
    public ResponseEntity<byte[]> export(
            @Parameter(example = "2026-08-01") @RequestParam LocalDate fromDate,
            @Parameter(example = "2026-08-31") @RequestParam LocalDate toDate) {
        byte[] xml = tallyExportService.exportXml(fromDate, toDate);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header("Content-Disposition",
                        "attachment; filename=\"tally-export-" + fromDate + "-to-" + toDate + ".xml\"")
                .body(xml);
    }
}
