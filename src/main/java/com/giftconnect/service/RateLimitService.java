package com.giftconnect.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded, in-memory fixed-window rate limiter used to blunt brute-force and
 * email-abuse attempts on the credential endpoints (login, registration, OTP
 * verification, OTP resend).
 *
 * <h2>Design</h2>
 * Each key (typically "endpoint + client IP") gets a counter that resets once
 * the window elapses. Counters are kept in a {@link ConcurrentHashMap}; the map
 * is hard-capped at {@code app.rate-limit.max-keys} entries so a flood of
 * distinct keys can never grow memory without bound. When the cap is exceeded
 * the limiter first purges expired windows and then, if still over the cap,
 * removes the oldest windows. This keeps the structure bounded under attack at
 * the cost of occasionally forgetting a very old key (acceptable: forgotten keys
 * start a fresh window, which is no worse than the window expiring).
 *
 * <h2>Configuration</h2>
 * <ul>
 *   <li>{@code app.rate-limit.enabled} (default true)</li>
 *   <li>{@code app.rate-limit.max-requests} per window (default 20)</li>
 *   <li>{@code app.rate-limit.window-seconds} (default 60)</li>
 *   <li>{@code app.rate-limit.max-keys} maximum tracked keys (default 10000)</li>
 * </ul>
 *
 * <h2>Limitations (single-instance limiter)</h2>
 * This limiter is deliberately per-process and in-memory. It is NOT shared
 * across application instances, so behind a load balancer the effective limit is
 * {@code max-requests * instanceCount} and a client can get a fresh budget from
 * each node. Counters also reset on restart. It is a best-effort abuse control,
 * not a distributed quota; a shared store (e.g. Redis) or a rate-limiting
 * gateway is required for a horizontally scaled deployment. The key is derived
 * from the client IP, which honors the first {@code X-Forwarded-For} entry when
 * present — so it assumes the app sits behind a trusted proxy that sets that
 * header (otherwise the header is client-spoofable and the limiter can be
 * evaded by rotating it).
 */
@Component
public class RateLimitService {

    private final boolean enabled;
    private final int maxRequests;
    private final long windowMillis;
    private final int maxKeys;
    private final Clock clock;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.max-requests:20}") int maxRequests,
            @Value("${app.rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${app.rate-limit.max-keys:10000}") int maxKeys,
            Clock clock) {
        this.enabled = enabled;
        this.maxRequests = Math.max(1, maxRequests);
        this.windowMillis = Math.max(1L, windowSeconds) * 1000L;
        this.maxKeys = Math.max(1, maxKeys);
        this.clock = clock;
    }

    /**
     * Records one request against {@code key} and reports whether it is allowed.
     *
     * @return {@code true} while the key is within its budget, {@code false} once
     *         the per-window maximum has been exceeded (caller should reply 429).
     */
    public boolean tryConsume(String key) {
        if (!enabled || key == null || key.isBlank()) {
            return true;
        }

        long now = clock.millis();
        Counter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStartMillis >= windowMillis) {
                return new Counter(now);
            }
            existing.count++;
            return existing;
        });

        // Enforce the hard cap on tracked keys after inserting a new one.
        if (counters.size() > maxKeys) {
            evict(now);
        }

        return counter.count <= maxRequests;
    }

    /** Number of keys currently tracked — used by tests to assert bounded growth. */
    public int trackedKeyCount() {
        return counters.size();
    }

    /**
     * Drops expired windows first, then (if the map is still over the cap)
     * removes the oldest windows until the cap is met. Called only when the cap
     * is exceeded, so it does not run on the hot path in normal operation.
     */
    private void evict(long now) {
        counters.entrySet().removeIf(e -> now - e.getValue().windowStartMillis >= windowMillis);

        int overflow = counters.size() - maxKeys;
        if (overflow <= 0) {
            return;
        }

        counters.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().windowStartMillis))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(counters::remove);
    }

    /** Mutable per-key fixed-window counter (guarded by the map's per-key compute lock). */
    private static final class Counter {
        private final long windowStartMillis;
        private int count;

        private Counter(long windowStartMillis) {
            this.windowStartMillis = windowStartMillis;
            this.count = 1;
        }
    }
}
