package com.hardware.erp.common.exception;

import org.springframework.http.HttpStatus;

public class AuthException extends BusinessException {

    /** The single message used for every credential failure. */
    public static final String GENERIC_FAILURE = "Invalid credentials";

    public AuthException(String message, String code) {
        super(message, HttpStatus.UNAUTHORIZED, code);
    }

    /**
     * Unknown account, wrong password, inactive account and locked account all
     * produce this identical response. Any variation - message, code, or even
     * response time - turns login into an account enumeration oracle.
     */
    public static AuthException invalidCredentials() {
        return new AuthException(GENERIC_FAILURE, "INVALID_CREDENTIALS");
    }
}
