package com.hardware.erp.common.image;

import com.hardware.erp.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * Shared by profile photo, shop logo and signature upload (CR-023) - one
 * place enforcing the same limit the database CHECK constraint and
 * application.yml's multipart cap agree on, so the three never drift.
 */
public final class ImageValidation {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final Set<String> PHOTO_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    /** Signatures should be transparent-background where possible - PNG only. */
    public static final Set<String> SIGNATURE_TYPES = Set.of("image/png");

    private ImageValidation() {
    }

    public static void validate(MultipartFile file, Set<String> allowedTypes) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose an image file to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(
                    "Image must be " + (MAX_BYTES / (1024 * 1024)) + "MB or smaller",
                    HttpStatus.UNPROCESSABLE_ENTITY, "FILE_TOO_LARGE");
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new BusinessException(
                    "Unsupported image format - use " + String.join(", ", allowedTypes),
                    HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_FILE_TYPE");
        }
    }
}
