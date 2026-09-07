package com.luuhoa.fincore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthenticationRateLimitFilterTest {

    @Test
    void rejectsARequestAfterTheConfiguredNumberOfAttempts() throws Exception {
        AuthenticationRateLimitFilter filter = filterWithOneAttempt();
        MockHttpServletRequest firstRequest = loginRequest();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        AtomicBoolean firstRequestReachedChain = new AtomicBoolean();

        filter.doFilter(firstRequest, firstResponse, (ignoredRequest, ignoredResponse) ->
                firstRequestReachedChain.set(true));

        MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        AtomicBoolean rejectedRequestReachedChain = new AtomicBoolean();
        filter.doFilter(loginRequest(), rejectedResponse, (ignoredRequest, ignoredResponse) ->
                rejectedRequestReachedChain.set(true));

        assertThat(firstRequestReachedChain).isTrue();
        assertThat(rejectedRequestReachedChain).isFalse();
        assertThat(rejectedResponse.getStatus()).isEqualTo(429);
        assertThat(rejectedResponse.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejectedResponse.getContentAsString()).contains("AUTH_RATE_LIMITED");
    }

    @Test
    void doesNotThrottleAnAuthenticationRouteOutsideTheProtectedSet() throws Exception {
        AuthenticationRateLimitFilter filter = filterWithOneAttempt();
        MockHttpServletRequest logout = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        logout.setRemoteAddr("127.0.0.1");
        AtomicInteger requestsReachingChain = new AtomicInteger();

        filter.doFilter(logout, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                requestsReachingChain.incrementAndGet());
        filter.doFilter(logout, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                requestsReachingChain.incrementAndGet());

        assertThat(requestsReachingChain).hasValue(2);
    }

    private AuthenticationRateLimitFilter filterWithOneAttempt() {
        AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(
                1,
                Duration.ofMinutes(1),
                Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC));
        return new AuthenticationRateLimitFilter(limiter);
    }

    private MockHttpServletRequest loginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
