package com.hardware.erp.subscription.exception;

import com.hardware.erp.common.exception.BusinessException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** CR-088. The shop has used every unit of a metered channel its plan includes this month. */
@Getter
public class UsageLimitReachedException extends BusinessException {

    public static final String CODE = "USAGE_LIMIT_REACHED";

    private final String usageKey;
    private final long usedCount;
    private final long includedCount;

    public UsageLimitReachedException(String usageKey, String label, long usedCount, long includedCount) {
        super("Usage limit reached: " + usedCount + " of " + includedCount + " " + label
                + " included this month have been used. Additional usage needs an add-on or a higher plan.",
                HttpStatus.TOO_MANY_REQUESTS, CODE);
        this.usageKey = usageKey;
        this.usedCount = usedCount;
        this.includedCount = includedCount;
    }
}
