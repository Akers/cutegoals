package com.cutegoals.instancemanagement.service;

import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter for authentication endpoints (Task 6.6).
 * Supports per-key rate limiting with configurable window and max attempts.
 * <p>
 * <strong>Limitation:</strong> Uses a process-local {@code ConcurrentHashMap}.
 * In multi-instance deployments, the rate limit state is NOT shared across
 * instances. After a restart, all rate limit counters are reset.
 * <p>
 * For production multi-instance deployments, replace this implementation with
 * a Redis-based rate limiter (e.g., using Redis Lua scripts with {@code INCR}
 * and {@code EXPIRE}) or a distributed token bucket. Configure the implementation
 * via {@code rate-limit.type}:
 * <pre>{@code
 * rate-limit:
 *   type: local     # in-memory (current, default)
 *   # type: redis   # future: Redis-backed distributed rate limiter
 * }</pre>
 *
 * @see <a href="https://redis.io/commands/INCR">Redis INCR with EXPIRE pattern</a>
 */
@Service
public class RateLimiterService {

    /**
     * In-memory attempt map. <strong>Not shared across instances.</strong>
     * For distributed rate limiting, replace this with a Redis-backed store.
     * Config key: {@code rate-limit.type} (values: {@code local}, {@code redis}).
     */
    private final Map<String, List<Long>> attemptMap = new ConcurrentHashMap<>();

    /**
     * Default thresholds per endpoint type.
     */
    public static final int MAX_LOGIN_ATTEMPTS = 10;
    public static final int MAX_INIT_ATTEMPTS = 5;
    public static final int MAX_REFRESH_ATTEMPTS = 20;
    public static final int MAX_PIN_ATTEMPTS = 5;
    public static final long WINDOW_MS = 60_000; // 1 minute

    /**
     * Check rate limit. Throws RATE_LIMITED if exceeded.
     *
     * @param key          the rate limit key (e.g., client IP, phone number)
     * @param maxAttempts  maximum attempts in the window
     * @param windowMs     window duration in milliseconds
     */
    public void checkRateLimit(String key, int maxAttempts, long windowMs) {
        long now = System.currentTimeMillis();
        attemptMap.compute(key, (k, timestamps) -> {
            if (timestamps == null) {
                List<Long> newList = new java.util.ArrayList<>();
                newList.add(now);
                return newList;
            }
            // Remove entries outside the window
            timestamps.removeIf(t -> (now - t) > windowMs);
            if (timestamps.size() >= maxAttempts) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
            timestamps.add(now);
            return timestamps;
        });
    }

    /**
     * Reset rate limit for a key (e.g., on successful login).
     */
    public void resetRateLimit(String key) {
        attemptMap.remove(key);
    }
}
