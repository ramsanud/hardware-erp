package com.hardware.erp.common.activity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * BUG-BE-002 (CR-072). The single row-writing step, in a bean of its own so
 * that {@code REQUIRES_NEW} actually applies.
 *
 * <p>It used to be a {@code protected write(...)} inside
 * {@link ActivityLogServiceImpl}, called as {@code this.write(...)} from
 * {@code created}/{@code updated}/{@code deleted}/{@code action}. That is
 * self-invocation: it never leaves the object, so it never passes through the
 * transactional proxy, and this project uses proxy-based AOP with no AspectJ
 * weaving anywhere. <strong>The annotation was inert</strong> and the log
 * write silently joined the caller's transaction, so a failed history row
 * could destroy the invoice it was describing - the precise outcome the
 * original comment claimed was impossible.
 *
 * <p><strong>This method deliberately does not catch anything.</strong> A JPA
 * constraint violation is not raised by {@code save()}; it is raised when the
 * persistence context flushes, which for {@code REQUIRES_NEW} happens as this
 * method returns and its transaction commits - i.e. inside the proxy, after
 * any {@code try} block written here has already exited. A catch placed here
 * would not see it, and a catch that swallowed it would leave the proxy
 * committing a transaction already marked rollback-only. The handler
 * therefore belongs to the caller, outside the boundary, and it is in
 * {@link ActivityLogServiceImpl}.
 *
 * <p>Crossing a bean boundary is what gives the propagation effect. This class
 * exists for that reason and must not be folded back in.
 */
@Component
@RequiredArgsConstructor
public class ActivityLogWriter {

    private final ActivityLogRepository activityLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(ActivityLog entry) {
        activityLogRepository.save(entry);
    }
}
