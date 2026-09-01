package com.hardware.erp.common.idempotency.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.common.idempotency.IdempotencyKeyReusedException;
import com.hardware.erp.common.idempotency.IdempotencyRecord;
import com.hardware.erp.common.idempotency.IdempotencyRecordRepository;
import com.hardware.erp.common.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyServiceImpl implements IdempotencyService {

    /** Never a real HTTP status - marks a row whose action has not finished yet. */
    private static final int IN_FLIGHT = 0;

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * MANDATORY, not REQUIRED - exactly DocumentSequenceService's reasoning
     * (CR-041). Executing outside a transaction would take the row lock and
     * release it immediately, which restores the very race this class exists
     * to remove: two concurrent duplicates would both see "not yet done" and
     * both run {@code action}.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T execute(Long tenantId, String operation, String idempotencyKey, Object requestPayload,
                         Class<T> responseType, Supplier<T> action) {
        String requestHash = hash(requestPayload);

        repository.insertPlaceholderIfAbsent(tenantId, idempotencyKey, operation, requestHash);

        // Blocks here if a concurrent request already holds this row's lock -
        // which is exactly the point. Whichever request commits first decides
        // the outcome for both.
        IdempotencyRecord record = repository.lockForUpdate(tenantId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "idempotency_record missing after insert for key " + idempotencyKey));

        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException(operation);
        }

        if (record.getResponseStatus() != IN_FLIGHT) {
            // Already completed - by this exact request on an earlier attempt,
            // or by a concurrent request that has since committed. Either way
            // action must NOT run again; replay what was stored.
            log.info("Idempotent replay for {} key={}", operation, idempotencyKey);
            return deserialize(record.getResponseBody(), responseType);
        }

        // First time this key has reached this point in a transaction that
        // will actually commit. Run the real action and store its result in
        // the SAME transaction, so the placeholder row and the action's own
        // writes commit or roll back together - a rolled-back action leaves
        // no completed record behind, and a retry with the same key is free
        // to try again from scratch.
        T result = action.get();
        record.setResponseStatus(200);
        record.setResponseBody(serialize(result));
        return result;
    }

    private String hash(Object payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(serialize(payload).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize idempotent request/response", ex);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not deserialize cached idempotent response", ex);
        }
    }
}
