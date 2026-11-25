package com.sardor.ecommerce.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private final boolean enabled;
    private final Duration window;
    private final int authLimit;
    private final int checkoutLimit;
    private final int paymentLimit;
    private final Cache<String, FixedWindow> buckets = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(3))
            .build();

    public RateLimitInterceptor(
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

        String key = request.getRemoteAddr() + ":" + rateLimitGroup(request);
        long now = System.nanoTime();
        FixedWindow bucket = buckets.get(key, ignored -> new FixedWindow(now));
        synchronized (bucket) {
            if (now - bucket.windowStartedAtNanos >= window.toNanos()) {
                bucket.windowStartedAtNanos = now;
                bucket.requests = 0;
            }

            if (bucket.requests >= limit) {
                long remainingNanos = window.toNanos() - (now - bucket.windowStartedAtNanos);
                long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(remainingNanos) + 1);
                response.setStatus(429);
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
                response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("""
                        {"status":429,"error":"Too Many Requests","message":"Rate limit exceeded. Retry after the indicated interval."}
                        """.trim());
                return false;
            }

            bucket.requests++;
            response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
            response.setHeader("X-RateLimit-Remaining", Integer.toString(limit - bucket.requests));
        }
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

    private static final class FixedWindow {
        private long windowStartedAtNanos;
        private int requests;

        private FixedWindow(long windowStartedAtNanos) {
            this.windowStartedAtNanos = windowStartedAtNanos;
        }
    }
}
