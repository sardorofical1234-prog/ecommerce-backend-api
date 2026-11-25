package com.sardor.ecommerce.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.rate-limit.backend", havingValue = "redis")
public class RedisRateLimitCounter implements RateLimitCounter {
    private static final DefaultRedisScript<List> INCREMENT_SCRIPT = new DefaultRedisScript<>();

    static {
        INCREMENT_SCRIPT.setScriptText("""
                local count = redis.call('INCR', KEYS[1])
                local ttl = redis.call('PTTL', KEYS[1])
                if count == 1 or ttl < 0 then
                    redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    ttl = redis.call('PTTL', KEYS[1])
                end
                return { count, ttl }
                """);
        INCREMENT_SCRIPT.setResultType(List.class);
    }

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitCounter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Decision increment(String key, Duration window) {
        try {
            List<?> result = redisTemplate.execute(
                    INCREMENT_SCRIPT,
                    List.of(key),
                    Long.toString(Math.max(1, window.toMillis()))
            );
            if (result == null || result.size() != 2
                    || !(result.get(0) instanceof Number count)
                    || !(result.get(1) instanceof Number ttl)) {
                throw new IllegalStateException("Redis returned an invalid rate-limit counter response");
            }
            return new Decision(count.longValue(), Math.max(1, ttl.longValue()));
        } catch (DataAccessException exception) {
            throw new RateLimitStoreUnavailableException("Redis rate-limit store is unavailable", exception);
        }
    }
}
