package com.hardware.erp.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base for every expected, user-facing failure. Never used for bugs. */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** 422: the request was well-formed but violates a business rule. */
    public BusinessException(String message) {
        this(message, HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION");
    }
}
