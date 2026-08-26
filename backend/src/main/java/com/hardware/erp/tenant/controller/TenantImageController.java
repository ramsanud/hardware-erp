package com.hardware.erp.tenant.controller;

import com.hardware.erp.tenant.service.TenantImageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Settings")
public class TenantImageController {

    private final TenantImageService imageService;

    @GetMapping("/logo")
    public ResponseEntity<byte[]> getLogo() {
        return imageService.getLogo()
                .map(logo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(logo.getContentType()))
                        .body(logo.getImageData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public void uploadLogo(@RequestParam("file") MultipartFile file) {
        imageService.uploadLogo(file);
    }

    @DeleteMapping("/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public void removeLogo() {
        imageService.removeLogo();
    }

    @GetMapping("/signature")
    public ResponseEntity<byte[]> getSignature() {
        return imageService.getSignature()
                .map(signature -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(signature.getContentType()))
                        .body(signature.getImageData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public void uploadSignature(@RequestParam("file") MultipartFile file) {
        imageService.uploadSignature(file);
    }

    @DeleteMapping("/signature")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public void removeSignature() {
        imageService.removeSignature();
    }

    @GetMapping("/upi-qr")
    public ResponseEntity<byte[]> getUpiQr() {
        return imageService.getUpiQr()
                .map(upiQr -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(upiQr.getContentType()))
                        .body(upiQr.getImageData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/upi-qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public void uploadUpiQr(@RequestParam("file") MultipartFile file) {
        imageService.uploadUpiQr(file);
    }

    @DeleteMapping("/upi-qr")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public void removeUpiQr() {
        imageService.removeUpiQr();
    }
}
