package com.sardor.ecommerce.config;

import java.time.Duration;

public interface RateLimitCounter {
    Decision increment(String key, Duration window);

    record Decision(long requestCount, long remainingWindowMillis) {
    }
}
