package com.luuhoa.fincore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class AuthenticationRateLimiterTest {

    @Test
    void permitsConfiguredAttemptsAndResetsAfterTheWindowExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T12:00:00Z"));
        AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(2, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire("/api/v1/auth/login:127.0.0.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("/api/v1/auth/login:127.0.0.1").allowed()).isTrue();
        AuthenticationRateLimiter.RateLimitDecision rejected = limiter.tryAcquire("/api/v1/auth/login:127.0.0.1");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(60);

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("/api/v1/auth/login:127.0.0.1").allowed()).isTrue();
    }

    @Test
    void keepsSeparateLimitsForSeparateAuthenticationRoutes() {
        AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(
                1,
                Duration.ofMinutes(1),
                Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC));

        assertThat(limiter.tryAcquire("/api/v1/auth/login:127.0.0.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("/api/v1/auth/login:127.0.0.1").allowed()).isFalse();
        assertThat(limiter.tryAcquire("/api/v1/auth/register:127.0.0.1").allowed()).isTrue();
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
