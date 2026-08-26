package com.hardware.erp.product.controller;

import com.hardware.erp.product.service.ProductImageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/products/{id}/image")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductImageController {

    private final ProductImageService productImageService;

    @GetMapping
    public ResponseEntity<byte[]> get(@PathVariable Long id) {
        return productImageService.get(id)
                .map(image -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(image.getContentType()))
                        .body(image.getImageData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    public void upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        productImageService.upload(id, file);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_MANAGE)")
    public void remove(@PathVariable Long id) {
        productImageService.remove(id);
    }
}
