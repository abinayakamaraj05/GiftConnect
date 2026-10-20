package com.giftconnect.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the bounded in-memory rate limiter. No Spring, no database —
 * a {@link MutableClock} makes window expiry deterministic.
 */
class RateLimitServiceTest {

    private static final long WINDOW_MS = 60_000L;

    /** Fixed-start clock the test can jump forward in time. */
    private static final class MutableClock extends Clock {
        private long millis;

        private MutableClock(long startMillis) {
            this.millis = startMillis;
        }

        void advance(long deltaMillis) {
            millis += deltaMillis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }

    private RateLimitService limiter(MutableClock clock, boolean enabled,
                                     int maxRequests, long windowSeconds, int maxKeys) {
        return new RateLimitService(enabled, maxRequests, windowSeconds, maxKeys, clock);
    }

    @Test
    void allowsUpToTheLimit_thenBlocks() {
        MutableClock clock = new MutableClock(0);
        RateLimitService service = limiter(clock, true, 3, 60, 100);

        assertTrue(service.tryConsume("login:1.2.3.4"));
        assertTrue(service.tryConsume("login:1.2.3.4"));
        assertTrue(service.tryConsume("login:1.2.3.4"));
        assertFalse(service.tryConsume("login:1.2.3.4"), "4th request exceeds a limit of 3");
    }

    @Test
    void aFreshWindowRestoresTheBudget() {
        MutableClock clock = new MutableClock(0);
        RateLimitService service = limiter(clock, true, 2, 60, 100);

        assertTrue(service.tryConsume("login:1.2.3.4"));
        assertTrue(service.tryConsume("login:1.2.3.4"));
        assertFalse(service.tryConsume("login:1.2.3.4"));

        clock.advance(WINDOW_MS);
        assertTrue(service.tryConsume("login:1.2.3.4"), "window elapsed -> counter resets");
    }

    @Test
    void differentKeysHaveIndependentBudgets() {
        MutableClock clock = new MutableClock(0);
        RateLimitService service = limiter(clock, true, 1, 60, 100);

        assertTrue(service.tryConsume("login:1.1.1.1"));
        assertFalse(service.tryConsume("login:1.1.1.1"));
        assertTrue(service.tryConsume("login:2.2.2.2"), "a different client is unaffected");
    }

    @Test
    void trackedKeysStayBoundedUnderAKeyFlood() {
        MutableClock clock = new MutableClock(0);
        int maxKeys = 3;
        RateLimitService service = limiter(clock, true, 1000, 60, maxKeys);

        for (int i = 0; i < 50; i++) {
            service.tryConsume("login:10.0.0." + i);
        }

        assertTrue(service.trackedKeyCount() <= maxKeys,
                "the map must never exceed max-keys (was " + service.trackedKeyCount() + ")");
    }

    @Test
    void expiredKeysAreEvictedWhenTheCapIsHit() {
        MutableClock clock = new MutableClock(0);
        RateLimitService service = limiter(clock, true, 1000, 60, 5);

        for (int i = 0; i < 5; i++) {
            service.tryConsume("login:old-" + i);
        }
        assertTrue(service.trackedKeyCount() <= 5);

        // Every old key expires, then a new key pushes the map over the cap so
        // eviction runs and clears all the dead windows.
        clock.advance(WINDOW_MS + 1);
        for (int i = 0; i < 5; i++) {
            service.tryConsume("login:new-" + i);
        }

        assertTrue(service.trackedKeyCount() <= 5);
    }

    @Test
    void disabledLimiterNeverBlocks() {
        MutableClock clock = new MutableClock(0);
        RateLimitService service = limiter(clock, false, 1, 60, 100);

        for (int i = 0; i < 100; i++) {
            assertTrue(service.tryConsume("login:1.2.3.4"));
        }
    }

    @Test
    void blankKeyIsNeverBlocked() {
        MutableClock clock = new MutableClock(0);
        RateLimitService service = limiter(clock, true, 1, 60, 100);

        assertTrue(service.tryConsume(null));
        assertTrue(service.tryConsume("   "));
    }
}
