package com.hardware.erp.common.activity;

import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * BUG-BE-002 (CR-072). A failed history write must not destroy the work it was
 * describing.
 *
 * Before the fix, {@code write(...)} was a protected method inside
 * ActivityLogServiceImpl carrying {@code @Transactional(REQUIRES_NEW)} and
 * invoked as {@code this.write(...)}. Self-invocation never reaches the
 * transactional proxy, so the annotation did nothing and the log write joined
 * the caller's transaction. Its {@code catch (DataAccessException)} then made
 * things worse rather than better: swallowing the exception hid the failure
 * while the surrounding transaction had already been marked rollback-only, so
 * the caller's real work - an invoice, a stock movement - was lost at commit
 * with nothing in the response to say why.
 *
 * This test fails against that arrangement and passes against the current one.
 * It writes a genuine row in an outer transaction, provokes a constraint
 * violation inside the writer, and then asserts the outer row survived.
 */
class ActivityLogWriterPropagationIT extends AbstractIntegrationTest {

    @Autowired private ActivityLogService activityLogService;
    @Autowired private ActivityLogRepository activityLogRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private final String marker = "ITProp" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        activityLogRepository.deleteAll(
                activityLogRepository.findAll().stream()
                        .filter(row -> marker.equals(row.getEntityType()))
                        .toList());
    }

    @Test
    @DisplayName("BUG-BE-002: a failing log write does not roll back the caller's own work")
    void failedLogWriteDoesNotRollBackTheCaller() {
        assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> {
            // The caller's real work.
            activityLogRepository.save(ActivityLog.builder()
                    .tenantId(1L)
                    .moduleCode("ITMOD")
                    .entityType(marker)
                    .entityLabel("the work that must survive")
                    .action(ActivityAction.CREATE)
                    .fullName("IT")
                    .build());

            // module_code is VARCHAR(30) and the service does not truncate it
            // (only entityLabel and remarks are), so this overflows and the
            // insert is refused. Driven through ActivityLogService, which is
            // the path every module actually uses - the handler lives there,
            // outside the REQUIRES_NEW boundary.
            activityLogService.created("M".repeat(40), marker, 1L, "doomed",
                    java.util.Map.of("name", "doomed"));
        }))
                .as("the failure is contained, never rethrown at the caller")
                .doesNotThrowAnyException();

        assertThat(activityLogRepository.findAll().stream()
                .filter(row -> marker.equals(row.getEntityType()))
                .map(ActivityLog::getEntityLabel))
                .as("the caller's committed work survived the logging failure")
                .containsExactly("the work that must survive");
    }
}
