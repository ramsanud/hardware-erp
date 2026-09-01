package com.hardware.erp.common.idempotency;

import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CR-051. Real PostgreSQL, same reason as DocumentSequenceServiceIT: the
 * guarantee this class exists to provide depends on SELECT ... FOR UPDATE
 * semantics that H2 does not faithfully reproduce.
 */
class IdempotencyServiceIT extends AbstractIntegrationTest {

    private static final long TENANT_A = 1L;

    @Autowired private IdempotencyService idempotencyService;
    @Autowired private TransactionTemplate transactionTemplate;

    private record Payload(String note) {}
    private record Result(String value) {}

    @Test
    @DisplayName("the same key and payload run the action exactly once, even from 20 concurrent callers")
    void concurrentCallsRunActionExactlyOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        Payload payload = new Payload("same request, every caller");
        AtomicInteger executions = new AtomicInteger(0);

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Result>> jobs = IntStream.range(0, threads)
                    .<Callable<Result>>mapToObj(i -> () -> transactionTemplate.execute(status ->
                            idempotencyService.execute(TENANT_A, "test.op", key, payload, Result.class, () -> {
                                executions.incrementAndGet();
                                return new Result("computed once");
                            })))
                    .toList();

            List<Future<Result>> futures = pool.invokeAll(jobs, 60, TimeUnit.SECONDS);
            List<Result> results = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new AssertionError("a call failed outright", e);
                }
            }).toList();

            assertThat(executions.get())
                    .as("the underlying action must run exactly once, however many callers race for it")
                    .isEqualTo(1);

            // Every caller, including the 19 that did not run the action,
            // must still get the SAME result back.
            Set<Result> distinct = Set.copyOf(results);
            assertThat(distinct).hasSize(1);
            assertThat(distinct.iterator().next().value()).isEqualTo("computed once");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("the same key with a different payload is rejected, not replayed")
    void differentPayloadSameKeyIsRejected() {
        String key = UUID.randomUUID().toString();

        transactionTemplate.execute(status -> idempotencyService.execute(
                TENANT_A,
                "test.op", key, new Payload("first request"), Result.class,
                () -> new Result("first result")));

        assertThatThrownBy(() -> transactionTemplate.execute(status -> idempotencyService.execute(
                TENANT_A,
                "test.op", key, new Payload("a completely different request"), Result.class,
                () -> new Result("must not run"))))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    @DisplayName("a rolled-back attempt leaves no record - a retry with the same key runs fresh")
    void rollbackLeavesNoCompletedRecord() {
        String key = UUID.randomUUID().toString();
        Payload payload = new Payload("will roll back first");

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            idempotencyService.execute(TENANT_A, "test.op", key, payload, Result.class,
                    () -> new Result("never committed"));
            throw new RuntimeException("simulated failure after the action ran");
        })).isInstanceOf(RuntimeException.class);

        AtomicInteger executions = new AtomicInteger(0);
        Result result = transactionTemplate.execute(status -> idempotencyService.execute(
                TENANT_A,
                "test.op", key, payload, Result.class, () -> {
                    executions.incrementAndGet();
                    return new Result("ran for real this time");
                }));

        assertThat(executions.get())
                .as("the rolled-back attempt must not have left a completed row behind")
                .isEqualTo(1);
        assertThat(result.value()).isEqualTo("ran for real this time");
    }

    @Test
    @DisplayName("different keys never interfere with each other")
    void differentKeysAreIndependent() {
        Payload payload = new Payload("same payload, different keys");

        Result a = transactionTemplate.execute(status -> idempotencyService.execute(
                TENANT_A,
                "test.op", UUID.randomUUID().toString(), payload, Result.class,
                () -> new Result("result A")));
        Result b = transactionTemplate.execute(status -> idempotencyService.execute(
                TENANT_A,
                "test.op", UUID.randomUUID().toString(), payload, Result.class,
                () -> new Result("result B")));

        assertThat(a.value()).isEqualTo("result A");
        assertThat(b.value()).isEqualTo("result B");
    }

    @Test
    @DisplayName("allocating outside a transaction is refused rather than silently racy")
    void requiresAnActiveTransaction() {
        assertThatThrownBy(() -> idempotencyService.execute(
                TENANT_A,
                "test.op", UUID.randomUUID().toString(), new Payload("x"), Result.class,
                () -> new Result("should not run")))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
}
