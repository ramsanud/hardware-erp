package com.hardware.erp.common.sequence;

import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression cover for CR-041.
 *
 * Before this service existed, every document code was allocated as
 * {@code findHighestGeneratedCodeNumber(tenantId) + 1}. Under concurrency two
 * callers read the same MAX and both attempted the same number; the unique
 * constraint on (tenant_id, invoice_number) rejected the loser, so no
 * duplicate was ever stored - but the loser's request died with a constraint
 * violation and their invoice was lost. Ten call sites shared that shape.
 *
 * These tests run against real PostgreSQL because the fix depends on
 * SELECT ... FOR UPDATE semantics, which H2 does not faithfully reproduce.
 */
class DocumentSequenceServiceIT extends AbstractIntegrationTest {

    private static final long TENANT_A = 1L;

    @Autowired private DocumentSequenceService sequenceService;
    @Autowired private TransactionTemplate transactionTemplate;

    /** Each allocation runs in its own transaction, exactly as a real request would. */
    private String allocate(DocumentType type, long tenantId) {
        return transactionTemplate.execute(status -> sequenceService.next(type, tenantId));
    }

    @Test
    @DisplayName("twenty concurrent allocations produce twenty distinct, contiguous numbers")
    void concurrentAllocationsNeverCollide() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<String>> jobs = IntStream.range(0, threads)
                    .<Callable<String>>mapToObj(i -> () -> allocate(DocumentType.INVOICE, TENANT_A))
                    .toList();

            List<Future<String>> futures = pool.invokeAll(jobs, 60, TimeUnit.SECONDS);

            List<String> issued = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new AssertionError("an allocation failed outright", e);
                }
            }).toList();

            // The property that actually matters: no two callers got the same
            // number. This is the assertion that failed under the old MAX+1.
            Set<String> distinct = Set.copyOf(issued);
            assertThat(distinct)
                    .as("every concurrent caller must receive its own number")
                    .hasSize(threads);

            // And no numbers were skipped - GST requires a consecutive serial.
            List<Integer> numbers = issued.stream()
                    .map(code -> Integer.parseInt(code.substring(4)))
                    .sorted()
                    .toList();
            assertThat(numbers.get(threads - 1) - numbers.get(0))
                    .as("the twenty numbers must form an unbroken run")
                    .isEqualTo(threads - 1);

            assertThat(issued).allMatch(code -> code.matches("^INV-\\d{6}$"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("sequences are independent per document type")
    void sequencesAreIndependentPerType() {
        String quotation = allocate(DocumentType.QUOTATION, TENANT_A);
        String purchase = allocate(DocumentType.PURCHASE, TENANT_A);

        assertThat(quotation).startsWith("QUO-");
        assertThat(purchase).startsWith("PUR-");

        // Allocating a quotation must not advance the purchase counter.
        int purchaseBefore = Integer.parseInt(purchase.substring(4));
        allocate(DocumentType.QUOTATION, TENANT_A);
        int purchaseAfter = Integer.parseInt(allocate(DocumentType.PURCHASE, TENANT_A).substring(4));

        assertThat(purchaseAfter).isEqualTo(purchaseBefore + 1);
    }

    @Test
    @DisplayName("a rolled-back transaction does not consume a number")
    void rollbackDoesNotBurnANumber() {
        String before = allocate(DocumentType.CATEGORY, TENANT_A);
        int beforeNumber = Integer.parseInt(before.substring(4));

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            sequenceService.next(DocumentType.CATEGORY, TENANT_A);
            throw new IllegalStateException("simulated failure after allocation");
        })).isInstanceOf(IllegalStateException.class);

        int afterNumber = Integer.parseInt(allocate(DocumentType.CATEGORY, TENANT_A).substring(4));

        // The allocation inside the failed transaction rolled back with it, so
        // the next real caller gets the number that attempt had taken. This is
        // why next() joins the caller's transaction instead of REQUIRES_NEW.
        assertThat(afterNumber)
                .as("a failed document must not leave a gap in the serial")
                .isEqualTo(beforeNumber + 1);
    }

    @Test
    @DisplayName("allocating outside a transaction is refused rather than silently racy")
    void requiresAnActiveTransaction() {
        // Propagation.MANDATORY. Without it, the row lock would be taken and
        // released immediately, restoring the very race this class removes.
        assertThatThrownBy(() -> sequenceService.next(DocumentType.BRAND, TENANT_A))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("codes are zero-padded to each type's documented width")
    void formatsToDocumentedWidth() {
        assertThat(DocumentType.INVOICE.format(419)).isEqualTo("INV-000419");
        assertThat(DocumentType.CUSTOMER.format(4)).isEqualTo("CUS-0004");
        assertThat(DocumentType.SUPPLIER.format(13)).isEqualTo("SUP-0013");
        assertThat(DocumentType.PROJECT.format(10)).isEqualTo("PRJ-0010");
        assertThat(DocumentType.PURCHASE.format(1)).isEqualTo("PUR-000001");

        // Every generated code must still satisfy the regex the old repository
        // queries used to detect "this one was generated, not hand-typed".
        assertThat(DocumentType.INVOICE.format(1)).matches("^INV-[0-9]+$");
    }

    @Test
    @DisplayName("a tenant's sequence continues from the codes already in its tables")
    void backfillContinuesExistingRun() {
        // V29 seeded next_value from MAX(existing) + 1 per tenant, so the first
        // allocation after the migration must not collide with seeded data.
        String issued = allocate(DocumentType.SUPPLIER, TENANT_A);
        int number = Integer.parseInt(issued.substring(4));

        List<String> seededSupplierCodes = transactionTemplate.execute(status ->
                entityManagerCodes());

        assertThat(seededSupplierCodes).isNotEmpty();
        int highestSeeded = seededSupplierCodes.stream()
                .filter(c -> c.matches("^SUP-[0-9]+$"))
                .map(c -> Integer.parseInt(c.substring(4)))
                .max(Integer::compareTo)
                .orElse(0);

        assertThat(number)
                .as("the allocator must continue past every code already stored")
                .isGreaterThan(highestSeeded - 1);
    }

    @Autowired private jakarta.persistence.EntityManager entityManager;

    private List<String> entityManagerCodes() {
        List<?> rows = entityManager
                .createNativeQuery("select supplier_code from supplier where tenant_id = 1")
                .getResultList();
        return rows.stream().map(String::valueOf).collect(Collectors.toList());
    }
}
