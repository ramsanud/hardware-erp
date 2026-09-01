package com.hardware.erp.common.idempotency;

import com.hardware.erp.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The same Idempotency-Key was sent with a different request body. Rejected
 * rather than replayed - serving invoice A's cached response to a request
 * that actually describes invoice B would be silently wrong, not merely
 * redundant. 409, matching the "this conflicts with existing state" meaning
 * that status already carries elsewhere in this codebase.
 */
public class IdempotencyKeyReusedException extends BusinessException {

    public IdempotencyKeyReusedException(String operation) {
        super("This request was already used for a different " + operation
                        + " request. Generate a new idempotency key for a new request.",
                HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
    }
}
