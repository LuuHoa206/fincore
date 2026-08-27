package com.luuhoa.fincore.identity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Limits public authentication attempts before a controller or password check runs. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
final class AuthenticationRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationRateLimitFilter.class);
    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh");

    private final AuthenticationRateLimiter rateLimiter;

    AuthenticationRateLimitFilter(AuthenticationRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isLimitedAuthenticationRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthenticationRateLimiter.RateLimitDecision decision = rateLimiter.tryAcquire(clientKey(request));
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("auth_rate_limited path={}", request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"AUTH_RATE_LIMITED\",\"message\":\"Too many authentication attempts. Try again later.\"}");
    }

    private boolean isLimitedAuthenticationRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && LIMITED_PATHS.contains(request.getRequestURI());
    }

    private String clientKey(HttpServletRequest request) {
        return request.getRequestURI() + ':' + request.getRemoteAddr();
    }
}
