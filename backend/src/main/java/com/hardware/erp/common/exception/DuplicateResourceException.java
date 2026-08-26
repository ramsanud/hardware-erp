package com.hardware.erp.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String field, Object value) {
        super("%s '%s' is already in use".formatted(field, value),
                HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }
}
