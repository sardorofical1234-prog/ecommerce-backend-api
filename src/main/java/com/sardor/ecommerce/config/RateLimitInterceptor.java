package com.sardor.ecommerce.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimitCounter counter;
    private final boolean enabled;
    private final Duration window;
    private final int authLimit;
    private final int checkoutLimit;
    private final int paymentLimit;

    public RateLimitInterceptor(
            RateLimitCounter counter,
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.window:1m}") Duration window,
            @Value("${app.rate-limit.auth-requests:20}") int authLimit,
            @Value("${app.rate-limit.checkout-requests:10}") int checkoutLimit,
            @Value("${app.rate-limit.payment-requests:10}") int paymentLimit
    ) {
        if (window.isZero() || window.isNegative()
                || authLimit < 1 || checkoutLimit < 1 || paymentLimit < 1) {
            throw new IllegalArgumentException("Rate limit window and limits must be positive");
        }
        this.counter = counter;
        this.enabled = enabled;
        this.window = window;
        this.authLimit = authLimit;
        this.checkoutLimit = checkoutLimit;
        this.paymentLimit = paymentLimit;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!enabled) {
            return true;
        }

        int limit = limitFor(request);
        if (limit == 0) {
            return true;
        }

        String key = "ecommerce:rate-limit:v1:" + rateLimitGroup(request) + ":" + request.getRemoteAddr();
        RateLimitCounter.Decision decision = counter.increment(key, window);
        long requestCount = decision.requestCount();

        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        if (requestCount > limit) {
            long retryAfterSeconds = Math.max(1, (decision.remainingWindowMillis() + 999) / 1000);
            response.setStatus(429);
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"status":429,"error":"Too Many Requests","message":"Rate limit exceeded. Retry after the indicated interval."}
                    """.trim());
            return false;
        }

        response.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(0, limit - requestCount)));
        return true;
    }

    private int limitFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return 0;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/auth/")) {
            return authLimit;
        }
        if ("/api/v1/checkout".equals(path)) {
            return checkoutLimit;
        }
        if (path.matches("/api/v1/orders/[^/]+/payments")) {
            return paymentLimit;
        }
        return 0;
    }

    private String rateLimitGroup(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/auth/")) {
            return "auth";
        }
        if ("/api/v1/checkout".equals(path)) {
            return "checkout";
        }
        return "payment";
    }
}
