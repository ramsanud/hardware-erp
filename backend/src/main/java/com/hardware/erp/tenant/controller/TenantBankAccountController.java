package com.hardware.erp.tenant.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.tenant.dto.TenantBankAccountRequest;
import com.hardware.erp.tenant.dto.TenantBankAccountResponse;
import com.hardware.erp.tenant.entity.TenantBankAccountQr;
import com.hardware.erp.tenant.service.TenantBankAccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/v1/settings/bank-accounts")
@RequiredArgsConstructor
@Tag(name = "Settings")
public class TenantBankAccountController {

    private final TenantBankAccountService bankAccountService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_VIEW)")
    public ApiResponse<List<TenantBankAccountResponse>> list() {
        return ApiResponse.ok(bankAccountService.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<TenantBankAccountResponse> create(@Valid @RequestBody TenantBankAccountRequest request) {
        return ApiResponse.ok(bankAccountService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<TenantBankAccountResponse> update(
            @PathVariable Long id, @Valid @RequestBody TenantBankAccountRequest request) {
        return ApiResponse.ok(bankAccountService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public void delete(@PathVariable Long id) {
        bankAccountService.delete(id);
    }

    @GetMapping("/{id}/reveal")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<String> reveal(@PathVariable Long id) {
        return ApiResponse.ok(bankAccountService.revealAccountNumber(id));
    }

    @PostMapping(value = "/{id}/qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<TenantBankAccountResponse> addQr(
            @PathVariable Long id, @RequestParam("label") String label, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(bankAccountService.addQr(id, label, file));
    }

    @DeleteMapping("/qr/{qrId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public void removeQr(@PathVariable Long qrId) {
        bankAccountService.removeQr(qrId);
    }

    @GetMapping("/qr/{qrId}/image")
    public ResponseEntity<byte[]> qrImage(@PathVariable Long qrId) {
        TenantBankAccountQr qr = bankAccountService.getQrImage(qrId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(qr.getContentType()))
                .body(qr.getImageData());
    }
}
