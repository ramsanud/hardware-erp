package com.hardware.erp.creditnote.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.creditnote.dto.CreditNoteRequest;
import com.hardware.erp.creditnote.dto.CreditNoteResponse;
import com.hardware.erp.creditnote.dto.CreditNoteSummaryResponse;
import com.hardware.erp.creditnote.entity.CreditNoteStatus;
import com.hardware.erp.creditnote.service.CreditNoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/credit-notes")
@RequiredArgsConstructor
@Tag(name = "Credit Notes")
public class CreditNoteController {

    private final CreditNoteService creditNoteService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CREDIT_NOTE_MANAGE)")
    public ApiResponse<CreditNoteResponse> create(
            @Valid @RequestBody CreditNoteRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(creditNoteService.create(request, idempotencyKey));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CREDIT_NOTE_VIEW)")
    public ApiResponse<PageResponse<CreditNoteSummaryResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CreditNoteStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(creditNoteService.search(search, status, fromDate, toDate, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CREDIT_NOTE_VIEW)")
    public ApiResponse<CreditNoteResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(creditNoteService.get(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CREDIT_NOTE_MANAGE)")
    public ApiResponse<CreditNoteResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(creditNoteService.cancel(id));
    }
}
