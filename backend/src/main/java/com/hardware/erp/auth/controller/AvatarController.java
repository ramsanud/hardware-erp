package com.hardware.erp.auth.controller;

import com.hardware.erp.auth.entity.UserAvatar;
import com.hardware.erp.auth.service.AvatarService;
import com.hardware.erp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Self-service only (CR-023) - a user's own photo, never another user's id
 * taken from a path variable, matching every other /auth/me endpoint.
 */
@RestController
@RequestMapping("/v1/auth/me/avatar")
@RequiredArgsConstructor
@Tag(name = "1. Authentication")
public class AvatarController {

    private final AvatarService avatarService;

    @GetMapping
    public ResponseEntity<byte[]> get() {
        Long userId = SecurityUtils.requireCurrentUser().getId();
        return avatarService.get(userId)
                .map(avatar -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(avatar.getContentType()))
                        .body(avatar.getImageData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upload(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.requireCurrentUser().getId();
        avatarService.upload(userId, file);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove() {
        Long userId = SecurityUtils.requireCurrentUser().getId();
        avatarService.remove(userId);
    }
}
