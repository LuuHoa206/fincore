package com.luuhoa.fincore.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Small per-instance fixed-window limiter for public authentication routes.
 * A distributed deployment should replace this implementation with a shared
 * gateway or Redis-backed limiter.
 */
public final class AuthenticationRateLimiter {

    private final int maximumAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, AttemptWindow> attemptsByClient = new HashMap<>();

    public AuthenticationRateLimiter(int maximumAttempts, Duration window) {
        this(maximumAttempts, window, Clock.systemUTC());
    }

    AuthenticationRateLimiter(int maximumAttempts, Duration window, Clock clock) {
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("Authentication rate limit must allow at least one attempt");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Authentication rate limit window must be positive");
        }
        this.maximumAttempts = maximumAttempts;
        this.window = window;
        this.clock = clock;
    }

    synchronized RateLimitDecision tryAcquire(String clientKey) {
        Instant now = clock.instant();
        attemptsByClient.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));

        AttemptWindow current = attemptsByClient.get(clientKey);
        if (current == null) {
            attemptsByClient.put(clientKey, new AttemptWindow(now.plus(window), 1));
            return RateLimitDecision.permitted();
        }
        if (current.attempts() >= maximumAttempts) {
            return RateLimitDecision.rejected(secondsUntil(current.expiresAt(), now));
        }

        attemptsByClient.put(clientKey, new AttemptWindow(current.expiresAt(), current.attempts() + 1));
        return RateLimitDecision.permitted();
    }

    private long secondsUntil(Instant expiresAt, Instant now) {
        return Math.max(1, Duration.between(now, expiresAt).toSeconds());
    }

    record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

        static RateLimitDecision permitted() {
            return new RateLimitDecision(true, 0);
        }

        static RateLimitDecision rejected(long retryAfterSeconds) {
            return new RateLimitDecision(false, retryAfterSeconds);
        }
    }

    private record AttemptWindow(Instant expiresAt, int attempts) {
    }
}
