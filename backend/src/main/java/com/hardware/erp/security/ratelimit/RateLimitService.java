package com.hardware.erp.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * In-process token buckets.
 *
 * A single-shop ERP runs one JVM, so a distributed backend would be operational
 * cost with no benefit. Buckets live in a Caffeine cache that evicts on idle,
 * which bounds memory under a scripted attack from many source addresses.
 *
 * State is lost on restart. That is acceptable: account lockout persists in the
 * database and is the durable control. Rate limiting is the cheap first layer.
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RateLimitProperties properties;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    public record Decision(boolean allowed, long retryAfterSeconds) {

    public static Decision allow() {
        return new Decision(true, 0);
    }
    }

    public Decision check(RateLimitRule rule, String key) {
        if (!properties.enabled()) {
            return Decision.allow();
        }
        int capacity = capacityFor(rule);
        if (capacity <= 0) {
            return Decision.allow();
        }

        Bucket bucket = buckets.get(rule.name() + '|' + key,
                ignored -> newBucket(capacity, rule.window()));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return Decision.allow();
        }
        long retryAfter = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        return new Decision(false, retryAfter);
    }

    private Bucket newBucket(int capacity, Duration window) {
        // Greedy refill spreads tokens across the window instead of releasing
        // the whole allowance at once, so a burst at the boundary is smoothed.
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, window)
                        .build())
                .build();
    }

    private int capacityFor(RateLimitRule rule) {
        return switch (rule) {
            case LOGIN_PER_IP -> properties.loginPerIpPerMinute();
            case LOGIN_PER_IDENTIFIER -> properties.loginPerIdentifierPerMinute();
            case FORGOT_PASSWORD_PER_IP -> properties.forgotPasswordPerIpPerHour();
            case FORGOT_PASSWORD_PER_IDENTIFIER -> properties.forgotPasswordPerIdentifierPerHour();
            case RESET_PASSWORD_PER_IP -> properties.resetPasswordPerIpPerHour();
            case REFRESH_PER_IP -> properties.refreshPerIpPerMinute();
            case REGISTER_PER_IP -> properties.registerPerIpPerHour();
            case PLATFORM_ADMIN_LOGIN_PER_IP -> properties.platformAdminLoginPerIpPerMinute();
        };
    }

    /** Test hook. Never called from application code. */
    public void reset() {
        buckets.invalidateAll();
    }
}
