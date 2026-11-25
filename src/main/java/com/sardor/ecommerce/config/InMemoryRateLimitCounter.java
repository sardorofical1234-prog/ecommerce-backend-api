package com.sardor.ecommerce.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.rate-limit.backend", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimitCounter implements RateLimitCounter {
    private final Cache<String, WindowBucket> buckets = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(3))
            .build();

    @Override
    public Decision increment(String key, Duration window) {
        long now = System.nanoTime();
        long windowNanos = window.toNanos();
        WindowBucket bucket = buckets.get(key, ignored -> new WindowBucket(now));
        synchronized (bucket) {
            if (now - bucket.startedAtNanos >= windowNanos) {
                bucket.startedAtNanos = now;
                bucket.requestCount = 0;
            }
            bucket.requestCount++;
            long elapsed = now - bucket.startedAtNanos;
            long remainingNanos = Math.max(1, windowNanos - elapsed);
            return new Decision(bucket.requestCount,
                    Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
        }
    }

    private static final class WindowBucket {
        private long startedAtNanos;
        private long requestCount;

        private WindowBucket(long startedAtNanos) {
            this.startedAtNanos = startedAtNanos;
        }
    }
}
