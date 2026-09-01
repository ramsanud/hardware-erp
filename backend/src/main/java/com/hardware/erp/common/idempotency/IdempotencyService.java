package com.hardware.erp.common.idempotency;

import java.util.function.Supplier;

/**
 * CR-051. Runs a write operation exactly once per idempotency key.
 *
 * A shop-counter double-click, a request retried after a timeout, or a slow
 * connection that makes the owner click "Create" twice must never create the
 * Sales Order / Delivery Challan / Credit Note / Invoice twice.
 */
public interface IdempotencyService {

    /**
     * Executes {@code action} under {@code idempotencyKey}, exactly once.
     *
     * <p>First call with a key: {@code action} runs, its result is stored,
     * and is returned.
     * <p>A later call with the SAME key and the SAME request payload: {@code
     * action} does NOT run again; the original stored result is returned.
     * <p>A later call with the SAME key and a DIFFERENT request payload: an
     * {@code IdempotencyKeyReusedException} is thrown. Replaying the wrong
     * request's result would be silently wrong, not merely redundant, so
     * this case is rejected rather than served from cache.
     *
     * <p>Must run inside an existing transaction - {@code
     * Propagation.MANDATORY} on the implementation, the same discipline
     * {@code DocumentSequenceService} uses and for the same reason: the
     * row lock this takes must be held until the caller's own writes commit
     * or roll back together with it. A concurrent duplicate request blocks
     * on that lock rather than racing a check-then-insert.
     *
     * @param tenantId       resolved by the CALLER via
     *                       {@code SecurityUtils.requireCurrentTenantId()},
     *                       never by this service itself - the same division
     *                       of responsibility {@code DocumentSequenceService}
     *                       uses. A low-level service reused from many call
     *                       sites should not re-derive identity that its
     *                       caller has already resolved and validated.
     * @param operation      identifies which endpoint this key belongs to,
     *                       so the same key reused against a DIFFERENT
     *                       operation is caught as a client bug rather than
     *                       silently returning the wrong cached response
     * @param idempotencyKey caller-supplied, typically a UUID from the
     *                       {@code Idempotency-Key} request header
     * @param requestPayload hashed, never stored verbatim - used only to
     *                       detect key reuse with a different request
     * @param responseType   the type {@code action} returns, needed to
     *                       deserialize a cached response on replay
     */
    <T> T execute(Long tenantId, String operation, String idempotencyKey, Object requestPayload,
                  Class<T> responseType, Supplier<T> action);
}
